# Documentation Deprecation Notices

> **Doc Meta**
> - **Purpose:** Central registry of deprecated documentation with replacement links and rationale
> - **Scope:** All tombstoned and deprecated documentation across AutoDense project
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-03

## Overview

This document tracks all deprecated documentation in the AutoDense project, providing clear redirection to current resources and maintaining historical context for archival purposes.

---

## Active Deprecations

### DEP-001: Project Status Consolidation (COMPLETED)

**Deprecated Files:**
- `docs/STATUSLOG.md` 
- `docs/SuccessLog.md`

**Replacement:** `docs/PROJECT_STATUS.md`

**Deprecation Date:** 2025-08-26

**Reason:** Eliminated duplicate project status tracking by consolidating overlapping content into single canonical source. Both deprecated files contained similar project milestone and achievement information.

**Status:** ✅ **COMPLETED** - Files properly tombstoned with redirection notices

**Archive Location:** Included in deprecated-current.tar.gz

---

### DEP-002: Build Documentation Consolidation (PLANNED)

**Deprecated Files:**
- `DEFINITIVE_BUILD_REFERENCE.md` (planned)

**Replacement:** `BUILD_GUIDE.md`

**Target Deprecation Date:** 2025-09-10

**Reason:** Consolidating overlapping build system documentation. DEFINITIVE_BUILD_REFERENCE contains comprehensive reference information but BUILD_GUIDE has better user experience flow. Content will be merged before deprecation.

**Status:** 🚧 **PLANNED** - Awaiting content consolidation

**Implementation Notes:**
- Must extract critical --no-exit flag warning
- Merge troubleshooting section  
- Update all cross-references before tombstoning

---

### DEP-003: Legacy Architecture Planning (PLANNED)

**Deprecated Files:**
- `docs/New plan.md`

**Replacement:** `docs/IMPLEMENTATION_PLAN.md` and `Architecture.md`

**Target Deprecation Date:** 2025-09-10

**Reason:** Legacy architectural planning document from 2024-11-20 represents superseded design thinking. Current handle-based architecture is now fully implemented and documented in more recent sources.

**Status:** 🚧 **PLANNED** - Requires content review for salvageable insights

**Implementation Notes:**
- Extract any valuable architectural principles not covered elsewhere
- Historical context may be worth preserving in archive
- Missing Doc Meta block - add before deprecation

---

## Deprecation Template

Use this template for tombstoning deprecated documents:

```markdown
---
status: deprecated
deprecated_on: YYYY-MM-DD
replaced_by: /path/to/replacement.md
owner: @github-handle
last-verified: YYYY-MM-DD
reason: Brief explanation of why deprecated
---

# Deprecated — This document has moved

**Status:** Deprecated  
**Date:** YYYY-MM-DD  
**Reason:** [Specific reason for deprecation]  
**New home:** [link to replacement](replacement.md)

## What changed
- [Brief summary of what happened to the content]
- [Where users should go instead]

> **Doc Meta**
> - **Owner:** @github-handle
> - **Last-verified:** YYYY-MM-DD
```

---

## Deprecation Process

### Step 1: Content Analysis
- [ ] Review deprecated file for unique content
- [ ] Identify all cross-references to the file
- [ ] Determine appropriate replacement/consolidation target

### Step 2: Content Migration
- [ ] Extract valuable content to survivor document(s)
- [ ] Update cross-references throughout project
- [ ] Test that all functionality remains accessible

### Step 3: Tombstoning
- [ ] Replace file content with deprecation template
- [ ] Include specific replacement links and rationale
- [ ] Add proper Doc Meta block
- [ ] Set deprecation date and owner

### Step 4: Archival
- [ ] Move original content to `archive/staging/`
- [ ] Update `archive/deprecated-current.tar.gz`
- [ ] Add entry to `archive/INDEX.txt`
- [ ] Clean staging directory

---

## Historical Context

### Pre-Consolidation State (Before 2025-08-26)
The AutoDense project suffered from documentation sprawl with multiple files tracking similar information:

- **Project status scattered**: STATUSLOG.md, SuccessLog.md, and PROJECT_STATUS.md all tracking milestones
- **Build procedures duplicated**: Multiple build guides with conflicting information
- **Architecture documentation fragmented**: Planning spread across multiple historical documents

### Post-Consolidation Benefits
- **Single source of truth** for each topic area
- **Eliminated contradiction** between overlapping documents
- **Improved user experience** with clear navigation paths
- **Reduced maintenance burden** through content consolidation

---

## Prevention Guidelines

### Avoiding Future Documentation Sprawl

1. **Check for existing coverage** before creating new documentation
2. **Enhance existing documents** rather than creating parallel content
3. **Use cross-references** liberally to avoid content duplication
4. **Regular consolidation reviews** to catch early duplication

### Early Warning Signs
- Multiple files with similar titles or topics
- Cross-references forming circular dependency graphs  
- User confusion about which document is authoritative
- Maintenance burden from updating multiple files for same changes

### Consolidation Triggers
- **Jaccard similarity >0.15** between documents on same topic
- **Contradictory information** discovered between related documents
- **User feedback** indicating confusion about document hierarchy
- **Maintenance overhead** from keeping multiple sources current

---

## Archive Index

### Current Archive Contents

**deprecated-current.tar.gz** (Last updated: 2025-08-26)
- `statuslog-20250826.md` - Original STATUSLOG.md content
- `successlog-20250826.md` - Original SuccessLog.md content

**Planned Archive Additions**
- `definitive-build-reference-20250910.md` - Build reference consolidation
- `legacy-architecture-plan-20241120.md` - Old architectural planning

### Archive Access
Archives are maintained as single rolling tarball: `archive/deprecated-current.tar.gz`

To access archived content:
```bash
cd archive
tar -tzf deprecated-current.tar.gz  # List contents
tar -xzf deprecated-current.tar.gz filename  # Extract specific file
```

---

## Maintenance Schedule

### Quarterly Reviews (Every 3 months)
- Scan for new documentation overlap
- Review deprecation notices for accuracy
- Update archive organization if needed
- Validate replacement links remain current

### Annual Deep Clean (Yearly)
- Full project documentation similarity analysis
- Archive rotation (create new tarball, archive old)
- Documentation standards compliance audit
- User feedback integration

---

*This deprecation registry is updated as part of documentation consolidation activities and WRAPUP session procedures.*