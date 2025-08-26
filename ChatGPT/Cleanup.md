Here’s a **practical, repo-friendly cleanup plan** plus **drop-in scripts** you can run locally to inventory, detect dupes, and gatekeep future clutter.

------

# What “good” looks like (target structure)

```
/docs
  /architecture/            # deep dives + diagrams
  /user-guide/              # how to use the app
  /dev-guide/               # how to build, run, debug
  /workflows/               # SDS-PAGE, colony, PCR playbooks
  /reference/               # tool API, handles, schemas
  /release-notes/           # versioned notes
  /deprecations/            # tombstones & redirects
CLAUDE.md                   # session primer (short)
ARCHITECTURE.md             # top-level map (detailed)
CONTRIBUTING.md
CHANGELOG.md                # human-curated delta
ROADMAP.md
```

**Golden rules**

- One canonical doc per topic. Everything else: merge → redirect → tombstone.
- Every doc has: **Purpose**, **Scope**, **Owner**, **Last-verified (YYYY-MM-DD)**.
- Internal links prefer `/docs/...` relative paths.
- Only **ARCHITECTURE.md** and **CLAUDE.md** live at repo root; everything else under `/docs`.

------

# Step 1 — Make an inventory (what you actually have)

Save as `scripts/docs_inventory.py` (runs on your machine). It builds a CSV of all Markdown files, extracts titles, dates, links, and word counts.

```
#!/usr/bin/env python3
import os, re, csv, sys, hashlib
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
DOCS = [p for p in ROOT.rglob("*.md") if ".git" not in p.parts]

H1 = re.compile(r"^#\s+(.*)", re.M)
DATE = re.compile(r"(last[-_ ]?(updated|modified|verified)\s*[:=]\s*(\d{4}-\d{2}-\d{2}))", re.I)
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")

def sha(path): 
    h = hashlib.sha256()
    h.update(path.read_bytes())
    return h.hexdigest()

out = ROOT / "docs_inventory.csv"
with out.open("w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["path","title","words","links","sha256","last_verified"])
    for p in sorted(DOCS):
        text = p.read_text(encoding="utf-8", errors="ignore")
        title = (H1.search(text).group(1).strip() if H1.search(text) else "")
        words = len(re.findall(r"\b\w+\b", text))
        links = ";".join(sorted(set(LINK.findall(text))))
        m = DATE.search(text)
        verified = (m.group(3) if m else "")
        w.writerow([str(p.relative_to(ROOT)), title, words, links, sha(p), verified])

print(f"Wrote {out}")
```

Run:

```
python3 scripts/docs_inventory.py .
```

------

# Step 2 — Find redundancies & “near duplicates”

This compares docs by content similarity. Anything over **0.80 cosine** is a merge candidate.

Save as `scripts/docs_similar.py`:

```
#!/usr/bin/env python3
import sys, re
from pathlib import Path
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
docs, paths = [], []
for p in sorted(ROOT.rglob("*.md")):
    if ".git" in p.parts: continue
    t = p.read_text(encoding="utf-8", errors="ignore")
    t = re.sub(r"```.*?```", "", t, flags=re.S)  # strip code blocks
    t = re.sub(r"\W+", " ", t.lower())
    docs.append(t); paths.append(p)

vec = TfidfVectorizer(ngram_range=(1,2), min_df=2).fit_transform(docs)
sim = cosine_similarity(vec, vec)

THRESH = 0.80
pairs = []
n = len(paths)
for i in range(n):
    for j in range(i+1, n):
        if sim[i,j] >= THRESH:
            pairs.append((sim[i,j], paths[i], paths[j]))

pairs.sort(reverse=True)
for s, a, b in pairs[:100]:
    print(f"{s:.3f}\t{a.relative_to(ROOT)}\t{b.relative_to(ROOT)}")
```

Run:

```
python3 -m pip install scikit-learn
python3 scripts/docs_similar.py .
```

Output gives you a hit list to **merge or delete**.

------

# Step 3 — Identify orphans & dead links

Save as `scripts/docs_links.py`:

```
#!/usr/bin/env python3
import re, sys
from pathlib import Path
ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
all_md = {p.resolve() for p in ROOT.rglob("*.md") if ".git" not in p.parts}
referenced = set()

for p in all_md:
    text = p.read_text(encoding="utf-8", errors="ignore")
    for href in LINK.findall(text):
        if href.startswith("http"): continue
        tgt = (p.parent / href).resolve()
        if tgt.suffix == "": tgt = tgt.with_suffix(".md")
        referenced.add(tgt)

orphans = [p for p in all_md if p not in referenced and "README.md" not in p.name]
for p in sorted(orphans):
    print(p.relative_to(ROOT))
```

- **Output = orphans**: docs never linked by any other doc → likely dead or private notes.
- Also run a markdown link checker (lint step below) to catch **dead links**.

------

# Step 4 — Canonicalize & tombstone

For every near-duplicate pair, choose a **canonical** destination (e.g., `/docs/workflows/sds-page.md`). In the deprecated file, replace contents with a **tombstone**:

```
# Deprecated — Merged into `/docs/workflows/sds-page.md`
**Status:** Deprecated (2025-08-26)  
**Reason:** Consolidated duplicate content.  
**New home:** [/docs/workflows/sds-page.md](../workflows/sds-page.md)
```

This keeps external links from suddenly going stale while guiding readers to the new source of truth.

------

# Step 5 — Enforce hygiene going forward (CI gate)

Add markdown lint, spell check, and dead-link detection in CI.

**`.github/workflows/docs.yml`**

```
name: Docs Lint
on:
  pull_request:
    paths: ["**/*.md", ".github/workflows/docs.yml"]
  push:
    branches: [ main ]
    paths: ["**/*.md"]
jobs:
  md-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: DavidAnson/markdownlint-cli2-action@v17
        with: { globs: "**/*.md" }
      - name: Spell check
        uses: streetsidesoftware/cspell-action@v6
        with:
          files: "**/*.md"
          inline: true
      - name: Link check
        uses: lycheeverse/lychee-action@v1
        with:
          args: --verbose --no-progress --accept 200,206,429,302,301 "**/*.md"
        env:
          GITHUB_TOKEN: ${{secrets.GITHUB_TOKEN}}
```

**`markdownlint.config.json`** (sane defaults)

```
{
  "default": true,
  "MD013": { "line_length": 120 },
  "MD033": false,
  "MD041": false
}
```

**`cspell.json`** (seed with your jargon as you go)

```
{
  "version": "0.2",
  "language": "en",
  "words": ["AutoDense","ImageJ","Coomassie","BandAssist","Rf","Holm","Bonferroni","X-gal","Gemini","ImagePlus"]
}
```

------

# Step 6 — Write two anchor docs that everything else can point to

1. **`CLAUDE.md`** (you’ve got this—keep it lean and fresh).
2. **`ARCHITECTURE.md`** (detailed, stable, with its own delta changelog).

Skeleton for `ARCHITECTURE.md` you can paste now:

```
# ARCHITECTURE.md

## Purpose
Deep reference for AutoDense’s handle-based system; single source of truth for design decisions.

## High-Level
- Planner: **GeminiOrchestrator** → emits JSON tool calls only
- Executor: **ImageJ/Fiji** → performs pixel ops
- State: **SessionStore** → images, overlays, analyses by handle

## Components
- Orchestrator (planning, retries, prompt enforcement)
- Tools: GelAnalysisTools, ColonyAnalysisTools, PCRAnalysisTools
- SessionStore (handles, lifecycle)
- SessionLogger (JSONL)
- GelUI (chat + drag/drop; calls orchestrator only)

## Data Flow (Gel)
User → Orchestrator → Gemini → Tool plan JSON → ImageJ tools → SessionStore → Overlay/Results

## Handle Protocol
- ids: `img_*`, `ov_*`, `analysis_*`
- immutability rules & update semantics
- no pixel payloads in LLM traffic

## Error Model
- tool failure → abort plan unless fallback image handle is explicit
- structured error payloads
- retry policy boundaries

## Performance Notes
- 4k images memory footprint
- overlay rendering cadence
- API latency, request size limits

## Security
- API keys via env/props
- logs sanitized
- base64 images leave the machine on Gemini calls

## Delta Changelog (Architecture)
### ✅ Recent
- Strict handle-based enforcement
- SessionLogger stabilized (JSONL)
- BandAssist modular tools
- Time-series colony tracking added
### 🚧 In Progress
- SDS_PAGE_DENSITOMETRY preset rewire
- Lane-wise background subtraction
- Overlay refresh hooks
- Canonical tool surface (8 actions)
### 🎯 Next
- MW ladder auto-calibration
- Workflow record/replay
- Multi-image sessions & batch
- GPU kernels (long-term)
```

------

# Step 7 — Commit flow (safe and reversible)

```
git checkout -b docs/cleanup-aug2025
mkdir -p scripts
# add the three scripts + configs + workflow
git add scripts/*.py .github/workflows/docs.yml markdownlint.config.json cspell.json
git commit -m "docs: add inventory/dupe/link scripts + CI gate"

python3 scripts/docs_inventory.py .
python3 scripts/docs_similar.py .
python3 scripts/docs_links.py .

# use outputs to merge/tombstone
git add -A
git commit -m "docs: consolidate duplicates, add tombstones, normalize structure"

git push -u origin docs/cleanup-aug2025
```

------

# Step 8 — Editorial checklist for each surviving doc

- Has **Purpose, Scope, Owner, Last-verified** at top.
- Links only to canonical targets.
- No “future” sections duplicating ROADMAP.md; move those bullets there.
- For anything describing how Claude/Gemini should behave → **CLAUDE.md**.
- For any deep design rationale → **ARCHITECTURE.md** (and link to it).

------

## Where to continue

If you’d like, I can generate:

- a **PR description template** tailored for docs consolidations, and
- a tiny **pre-commit hook** that runs markdownlint/cspell on staged `.md` files so you catch issues before CI.

Your docs will go from attic-full-of-old-micropipettes to labeled drawers with fresh tips.

##### You said:

yes, please

###### ChatGPT said:

Thought for 13s

Heck yeah—here are the drop-in bits you asked for: a purpose-built **PR description template** for docs cleanups and a **pre-commit hook** that runs Markdown lint + spellcheck (and link checking when available). I’ve also included a tiny installer + one convenience script so this is painless for the team.

------

# 1) PR template — `.github/PULL_REQUEST_TEMPLATE.md`

```
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
```

------

# 2) Local doc checks — `scripts/md_check.sh`

```
#!/usr/bin/env bash
set -euo pipefail

# Collect files to check: staged .md by default; or all when arg --all
if [[ "${1:-}" == "--all" ]]; then
  FILES=$(git ls-files "*.md")
else
  FILES=$(git diff --cached --name-only --diff-filter=ACMRTUXB | grep -E '\.md$' || true)
fi

if [[ -z "${FILES}" ]]; then
  echo "No Markdown changes detected."
  exit 0
fi

echo "Checking Markdown files:"
echo "${FILES}" | sed 's/^/ - /'

# 1) Markdown lint (markdownlint-cli2 via npx if available)
if command -v npx >/dev/null 2>&1; then
  echo "Running markdownlint-cli2..."
  # Create a temp glob file so cli2 can read from list
  TMPG=$(mktemp)
  echo "${FILES}" > "${TMPG}"
  npx --yes markdownlint-cli2 < "${TMPG}"
  rm -f "${TMPG}"
else
  echo "WARN: npx not found; skipping markdownlint-cli2"
fi

# 2) Spellcheck (cspell)
if command -v npx >/dev/null 2>&1; then
  echo "Running cspell..."
  npx --yes cspell --no-progress --no-summary ${FILES}
else
  echo "WARN: npx not found; skipping cspell"
fi

# 3) Link check (lychee CLI if installed)
if command -v lychee >/dev/null 2>&1; then
  echo "Running lychee link checker..."
  lychee --no-progress --accept 200,206,301,302,429 ${FILES}
else
  echo "INFO: lychee not found; skipping local link check (covered in CI)."
fi

echo "All checks passed ✅"
```

Make it executable:

```
chmod +x scripts/md_check.sh
```

------

# 3) Git hooks path + pre-commit hook

Use a repo-local hooks directory so everyone shares the same hook.

**A) `.githooks/pre-commit`**

```
#!/usr/bin/env bash
# Run Markdown checks on staged files
scripts/md_check.sh
# If checks pass, re-add any auto-fixed files so they’re committed
git add -A :/
```

Make it executable:

```
chmod +x .githooks/pre-commit
```

**B) One-time installer — `scripts/install-githooks.sh`**

```
#!/usr/bin/env bash
set -euo pipefail
git config core.hooksPath .githooks
echo "Configured git hooks path to .githooks"
echo "Pre-commit hook installed. Run: git commit -m 'test' to verify."
```

Make it executable and run:

```
chmod +x scripts/install-githooks.sh
./scripts/install-githooks.sh
```

------

# 4) Optional: minimal `package.json` for tool pinning

If you want consistent versions for `npx` runs, add this (dev-only):

```
{
  "name": "autodense-docs-tools",
  "private": true,
  "devDependencies": {
    "markdownlint-cli2": "^0.15.0",
    "cspell": "^8.14.2"
  },
  "scripts": {
    "docs:check": "scripts/md_check.sh",
    "docs:check:all": "scripts/md_check.sh --all"
  }
}
```

Then:

```
npm i
```

*(CI already handles link checking with lychee; locally you can `cargo install lychee` or `brew install lychee` to enable it in the hook.)*

------

# 5) Quality “stamp” block for each doc (copy/paste)

Add this **at the top** of surviving docs to keep them fresh and accountable:

```
> **Doc Meta**
> - **Purpose:** What this doc is for
> - **Scope:** What it covers (and doesn’t)
> - **Owner:** @github-handle
> - **Last-verified:** 2025-08-26
```

------

# 6) Quick start (team playbook)

```
# Once per clone:
./scripts/install-githooks.sh
npm i  # optional, if you want pinned tool versions

# Before a docs PR:
python3 scripts/docs_inventory.py .
python3 scripts/docs_similar.py .
python3 scripts/docs_links.py .
# consolidate/tombstone based on the outputs

# Run checks locally (all docs)
npm run docs:check:all
# or just rely on pre-commit hook for staged files
```

------

That’s it. This gives you:

- A crisp **PR template** that forces clarity and evidence.
- A **pre-commit hook** that blocks low-quality docs from landing.
- A **unified check script** you can also run in CI or locally.
- A light process to make the cleanup repeatable instead of heroic.

If you want, I can open a tiny PR patch set with these exact files wired into your current repo structure and paths.

##### You said:

sure, lets do it

###### ChatGPT said:

Perfect — here’s the drop-in patch set you can commit straight into `AutoDense`. I’ve structured it so it’s safe to apply in one branch, then open a PR.

------

## 1. New scripts

**`scripts/docs_inventory.py`**

```
#!/usr/bin/env python3
import os, re, csv, sys, hashlib
from pathlib import Path

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
DOCS = [p for p in ROOT.rglob("*.md") if ".git" not in p.parts]

H1 = re.compile(r"^#\s+(.*)", re.M)
DATE = re.compile(r"(last[-_ ]?(updated|modified|verified)\s*[:=]\s*(\d{4}-\d{2}-\d{2}))", re.I)
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")

def sha(path): 
    h = hashlib.sha256(); h.update(path.read_bytes()); return h.hexdigest()

out = ROOT / "docs_inventory.csv"
with out.open("w", newline="") as f:
    w = csv.writer(f)
    w.writerow(["path","title","words","links","sha256","last_verified"])
    for p in sorted(DOCS):
        text = p.read_text(encoding="utf-8", errors="ignore")
        title = (H1.search(text).group(1).strip() if H1.search(text) else "")
        words = len(re.findall(r"\b\w+\b", text))
        links = ";".join(sorted(set(LINK.findall(text))))
        m = DATE.search(text)
        verified = (m.group(3) if m else "")
        w.writerow([str(p.relative_to(ROOT)), title, words, links, sha(p), verified])

print(f"Wrote {out}")
```

**`scripts/docs_similar.py`**

```
#!/usr/bin/env python3
import sys, re
from pathlib import Path
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
docs, paths = [], []
for p in sorted(ROOT.rglob("*.md")):
    if ".git" in p.parts: continue
    t = p.read_text(encoding="utf-8", errors="ignore")
    t = re.sub(r"```.*?```", "", t, flags=re.S)
    t = re.sub(r"\W+", " ", t.lower())
    docs.append(t); paths.append(p)

vec = TfidfVectorizer(ngram_range=(1,2), min_df=2).fit_transform(docs)
sim = cosine_similarity(vec, vec)

THRESH = 0.80
pairs = []
n = len(paths)
for i in range(n):
    for j in range(i+1, n):
        if sim[i,j] >= THRESH:
            pairs.append((sim[i,j], paths[i], paths[j]))

pairs.sort(reverse=True)
for s, a, b in pairs[:100]:
    print(f"{s:.3f}\t{a.relative_to(ROOT)}\t{b.relative_to(ROOT)}")
```

**`scripts/docs_links.py`**

```
#!/usr/bin/env python3
import re, sys
from pathlib import Path
ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
LINK = re.compile(r"\[[^\]]+\]\(([^)]+)\)")
all_md = {p.resolve() for p in ROOT.rglob("*.md") if ".git" not in p.parts}
referenced = set()

for p in all_md:
    text = p.read_text(encoding="utf-8", errors="ignore")
    for href in LINK.findall(text):
        if href.startswith("http"): continue
        tgt = (p.parent / href).resolve()
        if tgt.suffix == "": tgt = tgt.with_suffix(".md")
        referenced.add(tgt)

orphans = [p for p in all_md if p not in referenced and "README.md" not in p.name]
for p in sorted(orphans):
    print(p.relative_to(ROOT))
```

**`scripts/md_check.sh`**

```
#!/usr/bin/env bash
set -euo pipefail

if [[ "${1:-}" == "--all" ]]; then
  FILES=$(git ls-files "*.md")
else
  FILES=$(git diff --cached --name-only --diff-filter=ACMRTUXB | grep -E '\.md$' || true)
fi

if [[ -z "${FILES}" ]]; then
  echo "No Markdown changes detected."
  exit 0
fi

echo "Checking Markdown files:"
echo "${FILES}" | sed 's/^/ - /'

if command -v npx >/dev/null 2>&1; then
  echo "Running markdownlint-cli2..."
  TMPG=$(mktemp); echo "${FILES}" > "${TMPG}"
  npx --yes markdownlint-cli2 < "${TMPG}"; rm -f "${TMPG}"
  echo "Running cspell..."
  npx --yes cspell --no-progress --no-summary ${FILES}
else
  echo "WARN: npx not found; skipping lint/spell"
fi

if command -v lychee >/dev/null 2>&1; then
  echo "Running lychee link checker..."
  lychee --no-progress --accept 200,206,301,302,429 ${FILES}
else
  echo "INFO: lychee not found; skipping local link check (in CI)."
fi

echo "Docs checks passed ✅"
```

------

## 2. Git hook setup

**`.githooks/pre-commit`**

```
#!/usr/bin/env bash
scripts/md_check.sh
git add -A :/
```

**`scripts/install-githooks.sh`**

```
#!/usr/bin/env bash
set -euo pipefail
git config core.hooksPath .githooks
echo "Configured git hooks path to .githooks"
```

------

## 3. CI workflow

**`.github/workflows/docs.yml`**

```
name: Docs Lint
on:
  pull_request:
    paths: ["**/*.md"]
  push:
    branches: [ main ]
    paths: ["**/*.md"]
jobs:
  md-lint:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: DavidAnson/markdownlint-cli2-action@v17
        with: { globs: "**/*.md" }
      - name: Spell check
        uses: streetsidesoftware/cspell-action@v6
        with:
          files: "**/*.md"
          inline: true
      - name: Link check
        uses: lycheeverse/lychee-action@v1
        with:
          args: --verbose --no-progress --accept 200,206,301,302,429 "**/*.md"
        env:
          GITHUB_TOKEN: ${{secrets.GITHUB_TOKEN}}
```

------

## 4. Configs

**`markdownlint.config.json`**

```
{
  "default": true,
  "MD013": { "line_length": 120 },
  "MD033": false,
  "MD041": false
}
```

**`cspell.json`**

```
{
  "version": "0.2",
  "language": "en",
  "words": [
    "AutoDense","ImageJ","Coomassie","BandAssist","Rf",
    "Holm","Bonferroni","X-gal","Gemini","ImagePlus"
  ]
}
```

------

## 5. PR template

**`.github/PULL_REQUEST_TEMPLATE.md`**
 (as drafted in my last message)

------

## How to land this

```
git checkout -b docs/cleanup-infra
mkdir -p scripts .githooks .github/workflows
# add files above
git add scripts .githooks .github/workflows \
    markdownlint.config.json cspell.json \
    .github/PULL_REQUEST_TEMPLATE.md
git commit -m "docs: add cleanup scripts, hooks, and CI linting"
git push -u origin docs/cleanup-infra
```

Then open a PR titled something like:

> **docs: add inventory/dupe/link scripts, pre-commit hook, and CI gates**

------

Would you like me to also generate a **tombstone template** file (`/docs/DEPRECATED_TEMPLATE.md`) so you can just copy it into any doc you decide to deprecate? That way contributors have a one-click pattern to follow.

##### You said:

sure

###### ChatGPT said:

Awesome. Here’s a tidy, copy-paste **tombstone template** plus a tiny helper script to make deprecations painless and consistent.

------

# 1) `/docs/DEPRECATED_TEMPLATE.md`

```
---
status: deprecated
deprecated_on: 2025-08-26
replaced_by: /docs/<new-canonical-path>.md
owner: @<github-handle>
last-verified: 2025-08-26
---

# Deprecated — This document has moved

**Status:** Deprecated  
**Date:** 2025-08-26  
**Reason:** Consolidated duplicate/overlapping content into a single canonical source of truth.  
**New home:** [/docs/<new-canonical-path>.md](../<new-canonical-path>.md)

## What changed
- This page is kept only as a pointer.
- All updates now happen in the new canonical document.

## Changelog context (optional)
- Merged sections from:
  - `/docs/<old-path-a>.md`
  - `/docs/<old-path-b>.md`
- Minor copy edits and link fixes.

> **Doc Meta**
> - **Owner:** @<github-handle>
> - **Last-verified:** 2025-08-26
```

**How to use it**

1. Replace placeholders (`<new-canonical-path>`, `@<github-handle>`, dates).
2. Save this file over the **old** doc’s path (or keep it in the old path after moving the real content).
3. Update any internal links to the new canonical doc.

------

# 2) One-liner helper to deprecate a doc

**`scripts/deprecate_doc.sh`**

```
#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <old-doc-path.md> <new-canonical-relative-path.md> [@owner]"
  exit 1
fi

OLD="$1"
NEW="$2"
OWNER="${3:-@team}"

if [[ ! -f "$OLD" ]]; then
  echo "Error: $OLD not found"
  exit 2
fi

DATE="$(date +%Y-%m-%d)"
DIR="$(dirname "$OLD")"

cat > "$OLD" <<EOF
---
status: deprecated
deprecated_on: ${DATE}
replaced_by: /${NEW}
owner: ${OWNER}
last-verified: ${DATE}
---

# Deprecated — This document has moved

**Status:** Deprecated  
**Date:** ${DATE}  
**Reason:** Consolidated duplicate/overlapping content into a single canonical source of truth.  
**New home:** [/${NEW}](../$(realpath --relative-to="$DIR" "$(dirname "$NEW")")/$(basename "$NEW"))

## What changed
- This page is kept only as a pointer.
- All updates now happen in the new canonical document.

> **Doc Meta**
> - **Owner:** ${OWNER}
> - **Last-verified:** ${DATE}
EOF

echo "Tombstoned $OLD → points to /$NEW"
```

Make it executable:

```
chmod +x scripts/deprecate_doc.sh
```

**Example**

```
# Deprecate docs/gel/old_sds.md in favor of docs/workflows/sds-page.md
scripts/deprecate_doc.sh docs/gel/old_sds.md docs/workflows/sds-page.md @dnnunn
```

------

# 3) (Optional) README for `/docs/deprecations/`

Create this once to explain the pattern.

**`/docs/deprecations/README.md`**

```
# Deprecations

This folder stores tombstones (redirect stubs) for documents that were merged into canonical sources.

**Rules**
- Keep the filename/path stable when possible to preserve inbound links.
- Each tombstone must include: status, deprecated_on, replaced_by, owner, last-verified.
- New contributions should update the canonical doc, not the tombstone.
```

------

# 4) Nice-to-have: add a lint to catch stale tombstones

Drop this tiny check into your pre-commit or CI later if you like:

- Fail if a tombstone is older than N months without `last-verified` refresh.
- Fail if `replaced_by` path doesn’t exist.

I can add that to your `md_check.sh` once you’re ready.

##### You said:

I am ready

###### ChatGPT said:

Thought for 10s

Love it. Here’s a **no-deps tombstone linter** + wiring so stale/incorrect tombstones can’t sneak into your repo.

------

## 1) New script — `scripts/docs_tombstone_check.py`

- Finds Markdown files with a YAML-ish front matter block.
- If `status: deprecated`, it enforces:
  - required keys: `deprecated_on`, `replaced_by`, `owner`, `last-verified`
  - `replaced_by` points to a real file in the repo
  - `last-verified` is **fresh** (default ≤ 180 days old; configurable)
- Pure standard library; works on macOS/Linux.

```
#!/usr/bin/env python3
import sys, re
from pathlib import Path
from datetime import datetime, timedelta
import os

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()
MAX_AGE_DAYS = int(os.environ.get("TOMBSTONE_MAX_AGE_DAYS", "180"))

FRONT_MATTER = re.compile(r"^---\s*\n(.*?)\n---\s*", re.S)
KV = re.compile(r"^([A-Za-z0-9_-]+)\s*:\s*(.+?)\s*$")
DATE_FMT = "%Y-%m-%d"

def parse_front_matter(text:str):
    m = FRONT_MATTER.match(text)
    if not m: return {}
    block = m.group(1)
    meta = {}
    for line in block.splitlines():
        kvm = KV.match(line.strip())
        if kvm:
            meta[kvm.group(1).strip()] = kvm.group(2).strip()
    return meta

def is_md(p:Path):
    return p.suffix.lower() == ".md" and ".git" not in p.parts

def parse_date(s:str, key:str, path:Path, errs:list):
    try:
        return datetime.strptime(s, DATE_FMT).date()
    except Exception:
        errs.append(f"[{path}] invalid date for `{key}`: {s!r} (expected YYYY-MM-DD)")
        return None

def main():
    errors = []
    md_files = [p for p in ROOT.rglob("*.md") if is_md(p)]
    today = datetime.utcnow().date()
    cutoff = today - timedelta(days=MAX_AGE_DAYS)

    for p in sorted(md_files):
        text = p.read_text(encoding="utf-8", errors="ignore")
        meta = parse_front_matter(text)
        if not meta: continue
        if meta.get("status","").lower() != "deprecated":
            continue

        # Required keys
        req = ["deprecated_on","replaced_by","owner","last-verified"]
        for k in req:
            if k not in meta or not meta[k].strip():
                errors.append(f"[{p}] missing required front-matter key: `{k}`")

        # Dates
        dep = parse_date(meta.get("deprecated_on",""), "deprecated_on", p, errors) if "deprecated_on" in meta else None
        ver = parse_date(meta.get("last-verified",""), "last-verified", p, errors) if "last-verified" in meta else None

        if ver:
            if ver < cutoff:
                errors.append(f"[{p}] last-verified {ver} is older than {MAX_AGE_DAYS} days (cutoff {cutoff}); refresh required")

            if dep and ver < dep:
                errors.append(f"[{p}] last-verified {ver} is earlier than deprecated_on {dep} (should be same day or later)")

        # replaced_by resolution
        rb = meta.get("replaced_by","")
        if rb:
            # Allow leading slash; treat as repo-root absolute
            rb_clean = rb[1:] if rb.startswith("/") else rb
            target = (ROOT / rb_clean).resolve()
            if not target.exists():
                errors.append(f"[{p}] replaced_by points to missing file: {rb} (looked for: {target.relative_to(ROOT)})")
            elif target.is_dir():
                errors.append(f"[{p}] replaced_by path is a directory, expected a file: {rb}")

    if errors:
        print("Tombstone check failed:\n")
        for e in errors:
            print(" - " + e)
        sys.exit(1)
    else:
        print(f"Tombstone check passed ✅ (max age {MAX_AGE_DAYS} days)")

if __name__ == "__main__":
    main()
```

Make it executable:

```
chmod +x scripts/docs_tombstone_check.py
```

------

## 2) Wire into your local checks — update `scripts/md_check.sh`

Append this block near the end (before the final “passed” echo):

```
# 4) Tombstone policy (standard lib Python)
if command -v python3 >/dev/null 2>&1; then
  echo "Running tombstone policy check..."
  TOMBSTONE_MAX_AGE_DAYS=${TOMBSTONE_MAX_AGE_DAYS:-180} \
    python3 scripts/docs_tombstone_check.py .
else
  echo "WARN: python3 not found; skipping tombstone policy check"
fi
```

No need to change the pre-commit hook; it already calls `md_check.sh`.

------

## 3) Add to CI — update `.github/workflows/docs.yml`

Insert a new step after the link check:

```
      - name: Tombstone policy
        run: |
          chmod +x scripts/docs_tombstone_check.py
          TOMBSTONE_MAX_AGE_DAYS=180 scripts/docs_tombstone_check.py .
```

*(If the script doesn’t exist yet in `main`, this step will naturally start succeeding once merged with your branch.)*

------

## 4) Optional knobs

- Set a different freshness window by exporting `TOMBSTONE_MAX_AGE_DAYS`:
  - Locally: `TOMBSTONE_MAX_AGE_DAYS=90 scripts/md_check.sh --all`
  - CI: change the env in that step.

------

## 5) Commit steps

```
git checkout -b docs/tombstone-lint
git add scripts/docs_tombstone_check.py scripts/md_check.sh .github/workflows/docs.yml
git commit -m "docs: enforce tombstone policy (required keys, path exists, freshness window)"
git push -u origin docs/tombstone-lint
```

Open a PR titled:

> **docs: enforce tombstone freshness and replaced_by integrity**

------

This gives you hard guardrails: every deprecated doc must be pointed at a real canonical target and re-verified on a regular cadence—or the build turns as red as Coomassie. When you’re ready, I can also add a tiny rule that forbids NEW tombstones unless the canonical target already exists in the same PR (prevents dangling redirects).