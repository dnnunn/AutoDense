# Documentation Consolidation Plan

> **Doc Meta**
> - **Purpose:** Action plan for consolidating redundant and overlapping AutoDense documentation
> - **Scope:** Specific keep/merge/deprecate decisions with implementation steps
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-09-03

## Executive Summary

Analysis of 86 markdown files across the AutoDense project identified 4 overlapping clusters and several contradictory statements. This plan provides specific actions to eliminate redundancy while preserving valuable content and maintaining documentation standards.

**Key Findings:**
- 2 files already properly deprecated and tombstoned
- 2 files require consolidation (build system documentation)  
- 1 legacy file should be deprecated (architecture planning)
- 4 contradictions requiring resolution

---

## Consolidation Actions

### Cluster C-001: Build System Documentation

| Cluster ID | Topic | Survivor | Merge From | Rationale | Owner | Due Date |
|------------|--------|----------|------------|-----------|-------|----------|
| C-001 | Build system procedures | `BUILD_GUIDE.md` | `DEFINITIVE_BUILD_REFERENCE.md` | BUILD_GUIDE has better UX flow; DEFINITIVE has comprehensive reference data | @davidnunn | 2025-09-10 |

**Action Details:**
- **Keep**: `BUILD_GUIDE.md` (7,066 chars, better organization, more user-friendly)
- **Merge from**: `DEFINITIVE_BUILD_REFERENCE.md` (12,273 chars, comprehensive troubleshooting section)
- **Deprecate**: None (DEFINITIVE_BUILD_REFERENCE will become redirect after merge)

### Cluster C-003: Legacy Architecture Planning  

| Cluster ID | Topic | Survivor | Deprecate | Rationale | Owner | Due Date |
|------------|--------|----------|-----------|-----------|-------|----------|
| C-003 | Architecture evolution | `IMPLEMENTATION_PLAN.md` | `docs/New plan.md` | New plan from 2024-11-20 represents superseded architecture thinking | @davidnunn | 2025-09-10 |

**Action Details:**
- **Keep**: `docs/IMPLEMENTATION_PLAN.md` (current strategic plan, actively maintained)
- **Deprecate**: `docs/New plan.md` (legacy from 2024-11-20, outdated handle-based architecture concepts)
- **Tombstone**: Convert to pointer with deprecation notice

### Cluster C-002: Project Status (Already Resolved ✅)

**Status**: Successfully consolidated on 2025-08-26
- **Survivor**: `docs/PROJECT_STATUS.md`
- **Deprecated**: `docs/STATUSLOG.md` and `docs/SuccessLog.md` (both properly tombstoned)
- **No further action required**

---

## Implementation Checklist

### Phase 1: Build Documentation Consolidation (Priority 1)

**Target**: `BUILD_GUIDE.md` ← `DEFINITIVE_BUILD_REFERENCE.md`

- [ ] **Extract content from DEFINITIVE_BUILD_REFERENCE.md**:
  - [ ] Copy "CRITICAL OPTIMIZER WARNING: --no-exit FLAG" section
  - [ ] Merge comprehensive troubleshooting section
  - [ ] Extract validation checklist
  - [ ] Copy complete directory structure reference

- [ ] **Enhance BUILD_GUIDE.md**:
  - [ ] Add --no-exit flag warning in prominent box  
  - [ ] Integrate troubleshooting procedures
  - [ ] Add validation checklist as appendix
  - [ ] Maintain existing UX-focused organization

- [ ] **Create deprecation notice in DEFINITIVE_BUILD_REFERENCE.md**:
  - [ ] Replace content with tombstone format
  - [ ] Add pointer to consolidated BUILD_GUIDE.md
  - [ ] Include consolidation date and rationale
  - [ ] Add proper Doc Meta block

- [ ] **Update cross-references**:
  - [ ] Search project for links to DEFINITIVE_BUILD_REFERENCE.md
  - [ ] Update links to point to BUILD_GUIDE.md
  - [ ] Update CLAUDE.md build references

### Phase 2: Legacy Architecture Documentation (Priority 2)

**Target**: Deprecate `docs/New plan.md`

- [ ] **Review content for salvageable insights**:
  - [ ] Extract any architectural principles not covered in current docs
  - [ ] Identify any useful handle-based design patterns
  - [ ] Note any historical context worth preserving

- [ ] **Create tombstone for New plan.md**:
  - [ ] Replace content with deprecation template
  - [ ] Point to current architecture documentation
  - [ ] Add historical context note
  - [ ] Set deprecation date and owner

- [ ] **Update architecture documentation**:
  - [ ] Add any salvaged insights to Architecture.md or IMPLEMENTATION_PLAN.md
  - [ ] Include brief note about architectural evolution

### Phase 3: Contradiction Resolution (Priority 3)

- [ ] **Resolve build command contradiction (CON-002)**:
  - [ ] Standardize on `./build.sh all` across all documentation
  - [ ] Update any references to `./build.sh build`
  - [ ] Add explanatory note about command choice

- [ ] **Add missing Doc Meta blocks (CON-004)**:
  - [ ] Add Doc Meta to `CLAUDE.md`
  - [ ] Add Doc Meta to `docs/New plan.md` (before deprecation)
  - [ ] Audit other files for missing blocks

- [ ] **Standardize Python commands (CON-003)**:
  - [ ] Replace `python -m` with `python3 -m` in examples
  - [ ] Update command references for consistency

---

## Archive Management

### Files to Archive
1. **DEFINITIVE_BUILD_REFERENCE.md** (after content merge)
   - Size: 12,273 chars
   - Reason: Consolidated into BUILD_GUIDE.md
   - Archive action: Move to `archive/staging/deprecated-build-reference-20250903.md`

2. **docs/New plan.md** (after review and tombstone)
   - Size: 15,038 chars  
   - Reason: Superseded architecture planning
   - Archive action: Move to `archive/staging/legacy-architecture-plan-20241120.md`

### Archive Process
```bash
# Create archive staging area
mkdir -p archive/staging

# Move deprecated files (after tombstoning)
mv DEFINITIVE_BUILD_REFERENCE.md archive/staging/deprecated-build-reference-20250903.md
mv docs/New\ plan.md archive/staging/legacy-architecture-plan-20241120.md

# Create/update tarball
cd archive
tar -czf deprecated-current.tar.gz staging/*
rm -rf staging/*
echo "2025-09-03: Added build reference and legacy architecture docs" >> INDEX.txt
```

---

## Quality Assurance

### Pre-Implementation Testing
- [ ] Verify all cross-references before making changes
- [ ] Test build commands work after consolidation
- [ ] Validate doc navigation remains functional

### Post-Implementation Validation  
- [ ] All consolidated content is accessible
- [ ] Deprecated files show clear redirection
- [ ] No broken internal links
- [ ] Build procedures work from consolidated guide
- [ ] DOCMAP.json updated to reflect changes

### Success Criteria
- [ ] BUILD_GUIDE.md contains all essential build information
- [ ] No overlapping build documentation remains
- [ ] Legacy architecture planning properly archived
- [ ] All contradictions resolved
- [ ] Documentation standards consistently applied
- [ ] Single authoritative source for each topic

---

## Maintenance Prevention

### Ongoing Practices
1. **Single Source of Truth**: Designate one canonical document per topic area
2. **Cross-Reference Discipline**: Always link to canonical sources, never duplicate
3. **Regular Consolidation Reviews**: Quarterly assessment of documentation growth
4. **Template Enforcement**: Require Doc Meta blocks for new documentation

### Early Warning Systems
1. **Content Similarity Monitoring**: Flag new documents with >60% similarity to existing content
2. **Cross-Reference Validation**: Automated link checking in CI pipeline  
3. **Documentation Size Alerts**: Flag when doc/ directory grows by >20% without consolidation review

---

## Timeline and Ownership

| Phase | Tasks | Owner | Due Date | Dependencies |
|-------|--------|-------|----------|-------------|
| Phase 1 | Build documentation consolidation | @davidnunn | 2025-09-10 | Content review complete |
| Phase 2 | Legacy architecture deprecation | @davidnunn | 2025-09-10 | Architecture review |
| Phase 3 | Contradiction resolution | @davidnunn | 2025-09-15 | Phase 1-2 complete |
| Archive | Deprecated content archival | @davidnunn | 2025-09-15 | All phases complete |

---

## Risk Mitigation

### Potential Issues
1. **Loss of Reference Information**: Comprehensive content in DEFINITIVE_BUILD_REFERENCE
   - **Mitigation**: Complete content review and merge before deprecation

2. **Broken Documentation Links**: Internal references to deprecated files
   - **Mitigation**: Full project search and link update before archival

3. **User Confusion**: Changes to established build procedures
   - **Mitigation**: Clear change notes and transition guidance in consolidated guide

---

*Generated by documentation consolidation analysis on 2025-09-03*