## Summary
Concise description of what changed and why. Focus on consolidation, deprecation, and canonical sources of truth.

## Scope of Change
- [ ] Consolidation (merged duplicates)
- [ ] Deprecation/tombstones added
- [ ] New canonical docs created
- [ ] Structure changes (moved/renamed paths)
- [ ] Link fixes
- [ ] Minor copy edits only

## Affected Docs (before → after)
<!-- List moved/merged files. Keep this short but explicit. -->
- docs/old/sds.md → docs/workflows/sds-page.md
- docs/gel/faq.md (deprecated) → tombstone pointing to /docs/workflows/sds-page.md

## Evidence / Artifacts
- Inventory CSV: `docs_inventory.csv` (updated)
- Similarity report: (paste top relevant pairs or attach)
- Orphans report: (list that were addressed or intentionally kept)

## Checklists
**Content Hygiene**
- [ ] Each surviving doc has **Purpose**, **Scope**, **Owner**, **Last-verified (YYYY-MM-DD)**
- [ ] Duplicated content merged into a **single** canonical doc
- [ ] Deprecated docs replaced with **tombstones** linking to the canonical doc
- [ ] Internal links updated to new canonical paths

**Automated Gates**
- [ ] Markdown lint passes (markdownlint-cli2)
- [ ] Spellcheck passes (cspell with project dictionary)
- [ ] Link check passes (lychee, if available locally; always in CI)

## Risks / Rollback
Any external links likely to break? Any internal bookmarks that changed? Provide quick rollback steps if needed.

## Post-Merge Tasks
- [ ] Update any external references (wikis, READMEs, pinned Slack posts)
- [ ] Set calendar reminder to re-verify **Last-verified** dates in 4–6 weeks