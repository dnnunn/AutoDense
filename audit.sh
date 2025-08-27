#!/usr/bin/env bash
set -euo pipefail

# -------------------- Config (override via env or flags) --------------------
API_CONFIG_FILE="${API_CONFIG_FILE:-api-config.properties}"
ISSUES_FILE="${ISSUES_FILE:-AUDIT_ISSUES.md}"
ROOT_DIR="${ROOT_DIR:-.}"
PROVIDERS="${PROVIDERS:-openai gemini grok}"
CONSENSUS="${CONSENSUS:-openai}"

die() { echo "error: $*" >&2; exit 1; }
have_cmd() { command -v "$1" >/dev/null 2>&1; }
mask() { local s="${1:-}"; [ -z "$s" ] && { echo "********"; return; }; [ "${#s}" -le 8 ] && echo "********" || echo "${s:0:3}********${s: -3}"; }

parse_prop() {
  awk -v k="$2" '
    BEGIN{FS="="}
    /^[[:space:]]*#/ {next}
    /^[[:space:]]*$/ {next}
    {
      key=$1; sub(/^[[:space:]]+|[[:space:]]+$/, "", key)
      if (key==k) {
        $1="";
        val=substr($0, index($0, "=")+1)
        sub(/^[[:space:]]+/, "", val); sub(/[[:space:]]+$/, "", val)
        if ((val ~ /^".*"$/) || (val ~ /^'\''.*'\''$/)) { val=substr(val,2,length(val)-2) }
        print val; exit
      }
    }
  ' "$1"
}

usage() {
  cat <<EOF
Usage: $(basename "$0") [options]

Options:
  -c <consensus>     Consensus provider (default: $CONSENSUS)
  -p "<providers>"   Space-separated providers (default: "$PROVIDERS")
  -r <root>          Repo root to audit (default: $ROOT_DIR)
  -i <issues-file>   Issues context file (default: $ISSUES_FILE)
  -a <api-config>    API config properties path (default: $API_CONFIG_FILE)
  -m "<msg>"         Commit message (optional; prompts if unstaged changes)
  -n                 Commit/push only; skip audit
  -h                 Help

Environment overrides:
  API_CONFIG_FILE, ISSUES_FILE, ROOT_DIR, PROVIDERS, CONSENSUS
EOF
}

COMMIT_MSG=""
COMMIT_PUSH_ONLY=false
while getopts ":c:p:r:i:a:m:nh" opt; do
  case "$opt" in
    c) CONSENSUS="$OPTARG" ;;
    p) PROVIDERS="$OPTARG" ;;
    r) ROOT_DIR="$OPTARG" ;;
    i) ISSUES_FILE="$OPTARG" ;;
    a) API_CONFIG_FILE="$OPTARG" ;;
    m) COMMIT_MSG="$OPTARG" ;;
    n) COMMIT_PUSH_ONLY=true ;;
    h) usage; exit 0 ;;
    \?) die "unknown option: -$OPTARG";;
    :)  die "option -$OPTARG requires an argument";;
  esac
done

have_cmd git    || die "git not found"
have_cmd python || die "python not found"
[ -f "$API_CONFIG_FILE" ] || die "missing $API_CONFIG_FILE"
[ -d "$ROOT_DIR/.git" ] || die "ROOT_DIR is not a git repo: $ROOT_DIR"

# -------------------- 1) Commit + push --------------------
cd "$ROOT_DIR"
if ! git diff --quiet || ! git diff --cached --quiet || [ -n "$(git ls-files --others --exclude-standard)" ]; then
  git add -A
  if [ -z "$COMMIT_MSG" ]; then
    read -r -p "Commit message: " COMMIT_MSG
    [ -z "$COMMIT_MSG" ] && COMMIT_MSG="chore(audit): pre-audit snapshot $(date +'%Y-%m-%d %H:%M:%S')"
  fi
  git commit -m "$COMMIT_MSG" || true
else
  echo "No changes to commit."
fi

BRANCH="$(git rev-parse --abbrev-ref HEAD)"
REMOTE="${REMOTE:-origin}"
if git ls-remote --exit-code --heads "$REMOTE" "$BRANCH" >/dev/null 2>&1; then
  git push "$REMOTE" "$BRANCH"
else
  git push -u "$REMOTE" "$BRANCH"
fi
echo "Pushed $BRANCH to $REMOTE."

$COMMIT_PUSH_ONLY && { echo "Commit/push only requested (-n). Exiting."; exit 0; }

# -------------------- 2) Load API keys from properties --------------------
get_prop(){ parse_prop "$API_CONFIG_FILE" "$1" || true; }

OPENAI_API_KEY="$(get_prop OPENAI_API_KEY)"
GEMINI_API_KEY="$(get_prop GEMINI_API_KEY)"
# Grok/xAI can be provided as XAI_API_KEY or GROK_API_KEY; export XAI_API_KEY for the orchestrator.
XAI_API_KEY="$(get_prop XAI_API_KEY)"
[ -z "$XAI_API_KEY" ] && XAI_API_KEY="$(get_prop GROK_API_KEY)"

[ -n "$OPENAI_API_KEY" ] && export OPENAI_API_KEY
[ -n "$GEMINI_API_KEY" ] && export GEMINI_API_KEY
[ -n "$XAI_API_KEY" ]    && export XAI_API_KEY

echo "Keys loaded:"
[ -n "${OPENAI_API_KEY:-}" ] && echo "  OPENAI_API_KEY = $(mask "$OPENAI_API_KEY")"
[ -n "${GEMINI_API_KEY:-}" ] && echo "  GEMINI_API_KEY = $(mask "$GEMINI_API_KEY")"
[ -n "${XAI_API_KEY:-}" ]    && echo "  XAI_API_KEY    = $(mask "$XAI_API_KEY")"

# -------------------- 3) Run orchestrator --------------------
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ORCH="$SCRIPT_DIR/audit_orchestrator.py"
[ -f "$ORCH" ] || die "cannot find audit_orchestrator.py alongside audit.sh"

# Ensure deps (quiet best-effort)
if ! python - <<'PY' >/dev/null 2>&1
import importlib, sys
mods = ["httpx","typer","pydantic","yaml","rich"]
sys.exit(0 if all(importlib.util.find_spec(m) for m in mods) else 1)
PY
then
  echo "Installing Python deps…"
  pip install -q httpx typer pydantic pyyaml tiktoken rich || die "pip install failed"
fi

# Build provider args
read -r -a PROVIDER_ARR <<< "$PROVIDERS"

set -x
python "$ORCH" run \
  --providers "${PROVIDER_ARR[@]}" \
  --consensus "$CONSENSUS" \
  --root "$ROOT_DIR" \
  --issues-file "$ISSUES_FILE"
set +x

python "$ORCH" latest || true
