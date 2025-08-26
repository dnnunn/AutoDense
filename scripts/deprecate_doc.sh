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