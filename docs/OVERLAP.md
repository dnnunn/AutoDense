# Documentation Overlap Analysis

> **Doc Meta**
> - **Purpose:** Identify redundant and overlapping documentation requiring consolidation
> - **Scope:** Duplicate detection analysis across all AutoDense markdown files
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-03

## Overview

This analysis identifies content overlaps, near-duplicates, and redundant documentation across the AutoDense project. Files are clustered by topic and similarity to facilitate consolidation decisions.

---

## Cluster C-001: Build System Documentation (HIGH OVERLAP)

**Average Jaccard Similarity**: 0.161 (Moderate overlap requiring review)

### Files in Cluster
- **BUILD_GUIDE.md** (7,066 chars, 2025-08-30) — **SUGGESTED SURVIVOR**
- **DEFINITIVE_BUILD_REFERENCE.md** (12,273 chars, 2025-08-31) — Merge candidate

### Overlap Analysis
- **Topic**: Build system procedures and environment setup
- **Jaccard similarity**: 0.161 (moderate overlap)
- **Difflib ratio**: 0.045 (low text similarity but shared concepts)

### Key Overlap Areas
Both documents cover:
- Python venv activation procedures
- Maven build commands
- JAR file locations (`autodense-plugin-0.1.0-SNAPSHOT.jar`)
- Classpath generation procedures
- Testing commands with absolute paths

### Consolidation Recommendation
**Action**: **MERGE** - Combine into single authoritative build guide
- **Survivor**: `BUILD_GUIDE.md` (more concise, better organized)
- **Merge from**: `DEFINITIVE_BUILD_REFERENCE.md` (comprehensive reference data)
- **Rationale**: BUILD_GUIDE has better UX flow; DEFINITIVE_BUILD has comprehensive troubleshooting

---

## Cluster C-002: Project Status Documentation (RESOLVED)

**Status**: Already consolidated ✅

### Files in Cluster
- **PROJECT_STATUS.md** (8,500 chars, 2025-08-26) — **SURVIVOR**
- **STATUSLOG.md** (500 chars, deprecated) — **DEPRECATED with pointer**
- **SuccessLog.md** (500 chars, deprecated) — **DEPRECATED with pointer**

### Resolution Status
**ALREADY RESOLVED**: These files were properly consolidated on 2025-08-26:
- Duplicate content merged into `PROJECT_STATUS.md`
- Old files converted to deprecation pointers with tombstone format
- Deprecation notices include replacement links and rationale

---

## Cluster C-003: Architecture Planning Documentation (MODERATE OVERLAP)

**Average Jaccard Similarity**: 0.068 (Low but conceptually related)

### Files in Cluster
- **IMPLEMENTATION_PLAN.md** (19,038 chars, 2025-08-26) — **SURVIVOR**
- **docs/New plan.md** (15,038 chars, 2024-11-20) — Review for consolidation

### Overlap Analysis
- **Topic**: Architecture evolution and implementation strategy
- **Jaccard similarity**: 0.068 (low lexical overlap)
- **Difflib ratio**: 0.011 (very low text similarity)

### Content Comparison
- **IMPLEMENTATION_PLAN.md**: Strategic plan for Fiji integration, 6-week roadmap, technical specifications
- **New plan.md**: Handle-based architecture design, orchestrator patterns, LLM integration strategy

### Consolidation Recommendation
**Action**: **DEPRECATE** `New plan.md` - content is outdated legacy planning
- **Rationale**: `New plan.md` from 2024-11-20 represents superseded architecture thinking
- **Current approach**: Handle-based architecture is now implemented (described in IMPLEMENTATION_PLAN.md)
- **Migration**: Extract any still-relevant architectural insights, then tombstone

---

## Cluster C-004: Documentation Navigation (NO OVERLAP)

**Status**: Well-structured hierarchy

### Files in Cluster
- **docs/README.md** — Central navigation hub with category links
- **docs/DOCUMENT_CATALOG.md** — Comprehensive file catalog with summaries

### Analysis
- **No overlap detected**: These serve complementary roles
- **README.md**: Quick navigation and getting started
- **DOCUMENT_CATALOG.md**: Comprehensive inventory with metadata
- **Relationship**: README links to catalog; catalog provides detailed inventory
- **Action**: **KEEP BOTH** - different purposes, no redundancy

---

## Non-Clustered Files (LOW SIMILARITY)

The following files show minimal overlap (<0.05 Jaccard) and serve distinct purposes:

### Core Documentation
- **Architecture.md** — Technical system reference
- **CLAUDE.md** — AI assistant session primer

### Analysis Documentation  
- **WorkflowPresets.md** — Gel densitometry workflows
- **BandAssist.md** — User-guided band detection
- **STREAMLINED_COLONY_ANALYSIS_SYSTEM.md** — Colony analysis system

These files have distinct scopes and should remain separate.

---

## Summary Statistics

- **Total files analyzed**: 86 markdown files
- **Overlapping clusters identified**: 4
- **Files requiring action**: 3
- **Already resolved**: 2 files (STATUSLOG/SuccessLog → PROJECT_STATUS)
- **High confidence overlaps**: 1 cluster (Build system documentation)
- **Deprecated files**: 3 total (including 2 already tombstoned)

---

## Recommended Actions

### Immediate (High Priority)
1. **Consolidate build documentation**: Merge BUILD_GUIDE.md ← DEFINITIVE_BUILD_REFERENCE.md
2. **Deprecate legacy planning**: Tombstone `New plan.md` with pointer to current architecture docs

### Future Maintenance
1. Monitor session summaries and planning docs in NextSteps/ for redundancy buildup
2. Consider quarterly consolidation review for documentation in rapidly changing areas
3. Implement pre-commit hooks to flag potential duplicate content creation

---

*Generated by documentation condenser analysis on 2025-09-03*