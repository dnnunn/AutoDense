# Session Wrap-up Procedures

> **Doc Meta**
> - **Purpose:** Standardized end-of-session procedures for documentation maintenance and project tracking
> - **Scope:** Session summaries, document catalog updates, issue tracking, and next steps planning
> - **Owner:** @davidnunn
> - **Last-verified:** 2025-08-26

## 📋 Document Catalog Maintenance

**REQUIRED: Update document catalog for any new/modified documents**

#### Update Document Catalog
1. **Identify Changes:** Review all documents created or substantially modified during session
2. **Update Catalog:** Add entries to `/docs/DOCUMENT_CATALOG.md` in appropriate category:
   - Include document path and concise 1-2 sentence summary  
   - Add word count (from `scripts/docs_inventory.py` if needed)
   - Set Last-verified date to current session date
   - Ensure proper categorization (Architecture, Workflows, Colony Analysis, etc.)
3. **Verify Format:** Ensure Doc Meta blocks present on all new/modified .md files
4. **Update Statistics:** Refresh total document count and verification percentage in catalog

**Template for new catalog entries:**
```markdown
| **path/file.md** | Concise 1-2 sentence summary of purpose and content | word_count | 2025-08-26 |
```

## 📝 Prepare Session Summary

#### Create Session Summary Document
Location: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/SessionSummaries`
Format: `Session_summary_Month_Date_Year.md`

Include:
- Key accomplishments and changes made
- Documents created/modified (reference catalog updates)
- Technical decisions and rationale
- Issues encountered and solutions
- Code changes and architectural updates

## 🎯 Prepare Next Steps Summary

#### Create Next Steps Document  
Location: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/NextSteps`
Format: `NextSteps_Month_Date_Year.md`

Include:
- Priority tasks for next session
- Unfinished work requiring continuation  
- Dependencies and blockers identified
- Recommended approach for pending tasks
- Links to relevant documentation

## 🐛 Prepare Issues Summary

#### Create Issues Document
Location: `/Users/davidnunn/Desktop/Apps/BetterDairy/AutoDense/Issues`  
Format: `Issues_Month_Date_Year.md`

Include:
- Bugs discovered during session
- Performance issues or concerns
- Documentation gaps identified
- System limitations or edge cases
- Suggested investigation approaches

## ✅ Pre-Commit Checklist

Before ending session, verify:
- [ ] Document catalog updated with all new/modified files
- [ ] All new .md files have required Doc Meta blocks
- [ ] Session summary captures key accomplishments
- [ ] Next steps clearly defined with priorities
- [ ] Issues documented with sufficient detail for follow-up
- [ ] No sensitive information (API keys, passwords) in commits
- [ ] Code compiles and tests pass (if applicable)

---

*This process ensures consistent documentation maintenance and project continuity across sessions.*
