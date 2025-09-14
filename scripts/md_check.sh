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
  npx --yes markdownlint-cli2 ${FILES}
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

# 4) Tombstone policy (standard lib Python)
if command -v python3 >/dev/null 2>&1; then
  echo "Running tombstone policy check..."
  TOMBSTONE_MAX_AGE_DAYS=${TOMBSTONE_MAX_AGE_DAYS:-180} \
    python3 scripts/docs_tombstone_check.py .
else
  echo "WARN: python3 not found; skipping tombstone policy check"
fi

# 5) Doc Meta block validation (standard lib Python)
if command -v python3 >/dev/null 2>&1; then
  echo "Running Doc Meta validation..."
  python3 scripts/docs_meta_check.py .
else
  echo "WARN: python3 not found; skipping Doc Meta validation"
fi

echo "Docs checks passed ✅"