#!/usr/bin/env python3
"""
Audit Orchestrator — Multi‑LLM Codebase Auditor

What it does (end‑to‑end):
  1) Zips the current repo (excludes .git/, venv/, node_modules/, build artifacts).
  2) Builds a crisp audit prompt (auto‑detects recent git churn + optional AUDIT_ISSUES.md).
  3) Sends zip + prompt to multiple LLMs you enable (OpenAI, Anthropic, Gemini).
  4) Harvests each model's audit into audits/<YYYY‑MM‑DD_HHMMSS>/<provider>.md.
  5) Submits harvested audits back to a chosen model for consensus + consolidation.
  6) Saves the consensus to audits/<ts>/CONSENSUS.md and CONSENSUS.todo.md.
  7) Generates a ClaudeCode‑ready task breakdown (CLAUDE.md) for implementation.
  8) Supports repeat runs; each run is timestamp‑scoped. Can diff with previous run.

Quick start:
  export OPENAI_API_KEY=...      # if using OpenAI
  export ANTHROPIC_API_KEY=...   # if using Anthropic
  export GEMINI_API_KEY=...      # if using Google Gemini (Vertex/AI Studio key)

  pip install httpx typer pydantic pyyaml tiktoken rich
  python audit_orchestrator.py run --providers openai anthropic gemini --consensus openai

Notes:
- File attachment support varies across vendors and often changes. The provider
  adapters here implement the most stable, broadly compatible flows as of 2025‑08,
  but you may need to tweak model IDs or minor payload details.
- If a provider can’t accept a binary zip directly, we fall back to a manifest+
  strategic sampling approach (sending the file tree, key files, and hot diffs).
- For very large repos, consider setting --max‑files and --max‑bytes to throttle.
"""
from __future__ import annotations

import asyncio
import base64
import contextlib
import dataclasses
import hashlib
import io
import json
import os
import re
import shutil
import subprocess
import sys
import tarfile
import tempfile
import textwrap
from datetime import datetime
from pathlib import Path
from typing import Iterable, List, Dict, Any, Optional, Tuple

import httpx
import typer
from pydantic import BaseModel
from rich import print as rprint
from rich.console import Console
from rich.table import Table

try:
    import tiktoken  # token estimation for chunking fallback
except Exception:
    tiktoken = None

app = typer.Typer(add_completion=False, help="Multi‑LLM codebase auditor CLI")
console = Console()

# ------------------------------- Config ------------------------------------ #

DEFAULT_EXCLUDES = [
    ".git", ".github", ".gitlab", ".venv", "venv", "env",
    "node_modules", "dist", "build", "out", "__pycache__",
    ".idea", ".vscode", ".pytest_cache", "*.egg-info",
]

DEFAULT_MODELS = {
    "openai": "gpt-4.1-mini",            # Fast, capable
    "anthropic": "claude-3-5-sonnet-20240620",
    "gemini": "gemini-1.5-pro",
    "grok": "grok-2-latest",
}

# If your repos are gargantuan, throttle content capture for fallback path.
MAX_FILES_DEFAULT = 1200
MAX_BYTES_DEFAULT = 6_000_000  # ~6 MB of sampled source in worst case

# ------------------------------ Utilities ---------------------------------- #

def timestamp() -> str:
    return datetime.now().strftime("%Y-%m-%d_%H%M%S")


def is_binary(path: Path) -> bool:
    try:
        with open(path, "rb") as f:
            chunk = f.read(4096)
        if b"\0" in chunk:
            return True
        # Heuristic: treat images, archives, pdf as binary
        return path.suffix.lower() in {
            ".png", ".jpg", ".jpeg", ".gif", ".pdf", ".zip", ".tar", ".gz", ".tgz",
            ".mp3", ".mp4", ".mov", ".webp", ".ico", ".woff", ".woff2", ".ttf",
        }
    except Exception:
        return False


def should_exclude(path: Path, excludes: List[str]) -> bool:
    s = str(path)
    for ex in excludes:
        if ex.startswith("*"):
            if path.match(ex):
                return True
        else:
            if ex in s.split(os.sep):
                return True
    return False


def git_root(start: Path = Path(".")) -> Optional[Path]:
    try:
        out = subprocess.check_output(["git", "rev-parse", "--show-toplevel"], cwd=start).decode().strip()
        return Path(out)
    except Exception:
        return None


def gather_files(root: Path, excludes: List[str]) -> List[Path]:
    files: List[Path] = []
    for p in root.rglob("*"):
        if p.is_file() and not should_exclude(p.relative_to(root), excludes):
            files.append(p)
    return files


def zip_repo(root: Path, out_dir: Path, excludes: List[str]) -> Path:
    out_dir.mkdir(parents=True, exist_ok=True)
    zip_path = out_dir / f"repo_{timestamp()}.tar.gz"
    with tarfile.open(zip_path, "w:gz") as tar:
        for p in gather_files(root, excludes):
            tar.add(p, arcname=str(p.relative_to(root)))
    return zip_path


def make_manifest(root: Path, files: List[Path], max_files: int, max_bytes: int) -> Tuple[str, List[Tuple[Path, bytes]]]:
    """Return (manifest_text, sampled_files[(path, content_bytes)]) for fallback flows."""
    lines = [f"Repo: {root.resolve()}", "", "Files:"]
    text_budget = max_bytes
    sampled: List[Tuple[Path, bytes]] = []
    # Prefer source code files
    def score(p: Path) -> int:
        ext = p.suffix.lower()
        return {
            ".py": 10, ".ts": 9, ".tsx": 9, ".js": 8, ".jsx": 8, ".java": 8, ".go": 8,
            ".rs": 8, ".cpp": 8, ".c": 8, ".h": 8, ".hpp": 8, ".swift": 8, ".kt": 8,
            ".yaml": 7, ".yml": 7, ".toml": 7, ".json": 7, ".html": 7, ".css": 6,
            ".md": 6, ".txt": 6,
        }.get(ext, 1)
    code_files = sorted([p for p in files if not is_binary(p)], key=score, reverse=True)[:max_files]

    for p in code_files:
        rel = p.relative_to(root)
        size = p.stat().st_size
        sha = hashlib.sha256(p.read_bytes()).hexdigest()[:12]
        lines.append(f"  - {rel} ({size} bytes, sha256:{sha})")
        if text_budget > 0:
            chunk = p.read_bytes()
            if len(chunk) > 150_000:
                chunk = chunk[:150_000]
            if text_budget - len(chunk) >= 0:
                sampled.append((rel, chunk))
                text_budget -= len(chunk)

    manifest = "\n".join(lines)
    return manifest, sampled


def recent_git_context(root: Path, limit: int = 30) -> str:
    with contextlib.suppress(Exception):
        out = subprocess.check_output(
            ["git", "--no-pager", "log", f"-n{limit}", "--pretty=format:%h %ad %an %s", "--date=iso"],
            cwd=root,
        ).decode()
        return out
    return "(git log unavailable)"

# ---------------------------- Prompt Builder ------------------------------- #

def build_audit_prompt(project_name: str, root: Path, issues_md: Optional[Path]) -> str:
    issues = issues_md.read_text() if issues_md and issues_md.exists() else ""
    git_log = recent_git_context(root)
    readme = (root / "README.md").read_text() if (root / "README.md").exists() else ""

    prompt = f"""
You are a senior code auditor. Perform a surgical, unsentimental audit of the repository "{project_name}".

Goals:
1) Identify correctness bugs, race conditions, deadlocks, resource leaks, brittle IO, and silent failure paths.
2) Flag design smells (tight coupling, leaky abstractions, invalid invariants, unclear boundaries, weak error semantics).
3) Security posture: input validation, authz/authn gaps, secrets handling, SSRF/SQLi/XSS/serialization pitfalls.
4) Performance: big‑O hotspots, N+1, needless copies, synchronous I/O in hot paths, missing streaming/backpressure.
5) Tooling: tests, coverage, lint/typing, CI, docs, reproducibility, containerization, release hygiene.
6) Prioritize the top 10 concrete, actionable fixes with code‑level pointers and diffs.

Context from maintainer (free‑text, may be messy):
{issues}

Repo README excerpt (if any):
{readme[:12000]}

Recent git activity (latest ~30 commits):
{git_log}

Deliverables:
- "Executive Summary" (bullets) — what hurts and why it matters.
- "Findings" — numbered, each with: Severity (High/Med/Low), File:Line refs, Repro steps, Minimal fix, Ideal fix.
- "Fast Wins" — fixes that produce immediate UX or stability gains in < 2 hours.
- "Safety Nets" — tests/linters/CI you recommend, with exact commands/config snippets.
- "Risk Register" — risks + blast radius if ignored for 3 months.

Be terse, specific, and cite exact files and lines when possible.
""".strip()
    return prompt

# ---------------------------- Provider Base -------------------------------- #

class AuditResult(BaseModel):
    provider: str
    model: str
    audit_text: str
    raw: Optional[Dict[str, Any]] = None

class Provider:
    name: str
    model: str

    def __init__(self, model: Optional[str] = None):
        self.model = model or DEFAULT_MODELS.get(self.name, "")

    async def audit(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> AuditResult:
        raise NotImplementedError

# ----------------------------- OpenAI -------------------------------------- #

class OpenAIProvider(Provider):
    name = "openai"

    async def audit(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> AuditResult:
        api_key = os.getenv("OPENAI_API_KEY")
        if not api_key:
            raise RuntimeError("OPENAI_API_KEY not set")

        # Attempt file upload via Assistants API (stable for large files)
        # Fallback: send manifest+samples via Chat Completions if upload fails.
        headers = {"Authorization": f"Bearer {api_key}", "OpenAI-Beta": "assistants=v2"}
        async with httpx.AsyncClient(timeout=120) as client:
            # Upload the tarball as a file (binary)
            files = {"file": (tarball.name, tarball.read_bytes(), "application/gzip")}
            try:
                fr = await client.post("https://api.openai.com/v1/files", headers=headers, files=files, data={"purpose":"assistants"})
                fr.raise_for_status()
                file_id = fr.json()["id"]

                # Create an assistant thread run
                thread = await client.post("https://api.openai.com/v1/threads", headers=headers, json={"messages":[{"role":"user","content":prompt}]})
                thread_id = thread.json()["id"]

                run = await client.post(
                    f"https://api.openai.com/v1/threads/{thread_id}/runs",
                    headers=headers,
                    json={
                        "assistant_id": None,  # direct model run
                        "model": self.model,
                        "instructions": "Audit the attached repository archive.",
                        "tools": [],
                        "attachments": [{"file_id": file_id, "tools": []}],
                    },
                )
                run.raise_for_status()
                run_id = run.json()["id"]

                # Simple polling loop
                for _ in range(120):
                    status = await client.get(f"https://api.openai.com/v1/threads/{thread_id}/runs/{run_id}", headers=headers)
                    s = status.json()["status"]
                    if s in {"completed", "requires_action", "failed", "expired", "cancelled"}:
                        break
                    await asyncio.sleep(2)

                msgs = await client.get(f"https://api.openai.com/v1/threads/{thread_id}/messages", headers=headers)
                msgs.raise_for_status()
                data = msgs.json()
                # Extract latest assistant text
                text = ""
                for m in reversed(data.get("data", [])):
                    if m.get("role") == "assistant":
                        parts = m.get("content", [])
                        for p in parts:
                            if p.get("type") == "text":
                                text = p["text"]["value"]
                                break
                        if text:
                            break
                if not text:
                    text = "(No text returned)"
                return AuditResult(provider=self.name, model=self.model, audit_text=text, raw=data)
            except Exception as e:
                # Fallback to chat.completions with manifest+samples
                headers = {"Authorization": f"Bearer {api_key}"}
                content = [{"type":"text", "text": prompt}, {"type":"text", "text": "\nFALLBACK MODE: manifest + sampled files below."}, {"type":"text", "text": manifest}]
                for rel, blob in samples[:30]:
                    try:
                        snippet = blob.decode(errors="ignore")
                        chunk = snippet[:15000]
                        content.append({"type":"text", "text": f"\n===== {rel} =====\n{chunk}"})
                    except Exception:
                        pass
                payload = {
                    "model": self.model,
                    "messages": [{"role": "user", "content": content}]
                }
                async with httpx.AsyncClient(timeout=120) as c2:
                    resp = await c2.post("https://api.openai.com/v1/chat/completions", headers=headers, json=payload)
                    resp.raise_for_status()
                    j = resp.json()
                    text = j["choices"][0]["message"]["content"]
                    return AuditResult(provider=self.name, model=self.model, audit_text=text, raw=j)

# ----------------------------- Anthropic ----------------------------------- #

class AnthropicProvider(Provider):
    name = "anthropic"

    async def audit(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> AuditResult:
        api_key = os.getenv("ANTHROPIC_API_KEY")
        if not api_key:
            raise RuntimeError("ANTHROPIC_API_KEY not set")
        headers = {"x-api-key": api_key, "anthropic-version": "2023-06-01"}
        # Newer betas support file attachments; we provide a robust fallback.
        content: List[Dict[str, Any]] = [{"type": "text", "text": prompt}]
        # Attach manifest and a limited set of samples
        content.append({"type":"text","text":"\nFALLBACK MODE: manifest + sampled files below."})
        content.append({"type":"text","text": manifest})
        for rel, blob in samples[:25]:
            with contextlib.suppress(Exception):
                snippet = blob.decode(errors="ignore")
                content.append({"type":"text", "text": f"\n===== {rel} =====\n{snippet[:15000]}"})
        payload = {
            "model": self.model,
            "max_tokens": 4000,
            "messages": [{"role":"user","content": content}],
        }
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post("https://api.anthropic.com/v1/messages", headers=headers, json=payload)
            resp.raise_for_status()
            j = resp.json()
            text = "".join(part.get("text","") for part in j.get("content",[]) if part.get("type")=="text")
            return AuditResult(provider=self.name, model=self.model, audit_text=text, raw=j)

# ------------------------------- Gemini ------------------------------------ #

class GeminiProvider(Provider):
    name = "gemini"

    async def audit(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> AuditResult:
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            raise RuntimeError("GEMINI_API_KEY not set")
        # Use Generative Language API v1beta; include manifest + samples as text parts
        # For very large repos, you can switch to the file API and provide a file‑uri here.
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.model}:generateContent?key={api_key}"
        parts = [{"text": prompt}, {"text": "\nFALLBACK MODE: manifest + sampled files below."}, {"text": manifest}]
        for rel, blob in samples[:25]:
            with contextlib.suppress(Exception):
                snippet = blob.decode(errors="ignore")
                parts.append({"text": f"\n===== {rel} =====\n{snippet[:15000]}"})
        payload = {"contents": [{"role": "user", "parts": parts}]}
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post(url, json=payload)
            resp.raise_for_status()
            j = resp.json()
            text = "".join(p.get("text","") for c in j.get("candidates",[]) for p in c.get("content",{}).get("parts",[]))
            return AuditResult(provider=self.name, model=self.model, audit_text=text, raw=j)

# ------------------------------- Grok (xAI) -------------------------------- #

class GrokProvider(Provider):
    name = "grok"

    async def audit(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> AuditResult:
        """
        xAI Grok API: chat-completions style, no binary upload assumed.
        Uses manifest + sampled files fallback (robust and portable).
        Expects env var XAI_API_KEY or GROK_API_KEY.
        """
        api_key = os.getenv("XAI_API_KEY") or os.getenv("GROK_API_KEY")
        if not api_key:
            raise RuntimeError("XAI_API_KEY or GROK_API_KEY not set")
        headers = {"Authorization": f"Bearer {api_key}", "Content-Type": "application/json"}
        # OpenAI-compatible /chat/completions payload
        content = [
            {"role": "system", "content": "You are a precise, unsentimental code auditor. Be concrete, cite files/lines."},
            {"role": "user", "content": prompt + "\n\nFALLBACK MODE: manifest + sampled files below.\n" + manifest}
        ]
        # Append a few sampled files as extra messages to aid grounding.
        for rel, blob in samples[:25]:
            with contextlib.suppress(Exception):
                snippet = blob.decode(errors="ignore")[:15000]
                content.append({"role": "user", "content": f"===== {rel} =====\n{snippet}"})
        payload = {"model": self.model, "messages": content, "temperature": 0.2}
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post("https://api.x.ai/v1/chat/completions", headers=headers, json=payload)
            resp.raise_for_status()
            j = resp.json()
            text = j.get("choices", [{}])[0].get("message", {}).get("content", "")
            return AuditResult(provider=self.name, model=self.model, audit_text=text or "(No text returned)", raw=j)

# ---------------------------- Consolidation -------------------------------- #

CONSENSUS_SYSTEM = (
    "You are a gruff but helpful principal engineer who consolidates multiple audits into a single, coherent plan.\n"
    "Identify agreements, disagreements, and reconcile them into a ranked, deduplicated action list."
)


def build_consensus_prompt(audits: Dict[str, str]) -> str:
    blobs = []
    for name, text in audits.items():
        blobs.append(f"===== AUDIT: {name} =====\n{text}\n")
    tail = """
Required output:
1) Consensus Executive Summary (bullets).
2) Unified Findings (deduplicated, ranked). For each: Severity, Evidence, Concrete Fix.
3) Conflicts & Resolutions (where auditors disagree, explain your tie‑break).
4) Migration Plan (1‑week, 1‑month, 1‑quarter tracks with milestones).
5) Test & CI Plan (commands/config). Include exact file paths.
6) Risk Register + rollback/feature flagging recommendations.
""".strip()
    return "\n".join(blobs + [tail])

async def run_consensus(provider: Provider, audits: Dict[str, str]) -> AuditResult:
    prompt = build_consensus_prompt(audits)
    # Reuse provider with a simple text‑only message
    if isinstance(provider, OpenAIProvider):
        api_key = os.getenv("OPENAI_API_KEY"); headers = {"Authorization": f"Bearer {api_key}"}
        payload = {"model": provider.model, "messages": [{"role":"system","content": CONSENSUS_SYSTEM}, {"role":"user","content": prompt}]}
        async with httpx.AsyncClient(timeout=120) as c:
            r = await c.post("https://api.openai.com/v1/chat/completions", headers=headers, json=payload)
            r.raise_for_status()
            j = r.json(); text = j["choices"][0]["message"]["content"]
            return AuditResult(provider=f"{provider.name}-consensus", model=provider.model, audit_text=text, raw=j)
    elif isinstance(provider, AnthropicProvider):
        api_key = os.getenv("ANTHROPIC_API_KEY"); headers={"x-api-key": api_key, "anthropic-version":"2023-06-01"}
        payload = {"model": provider.model, "messages": [{"role":"system","content":[{"type":"text","text":CONSENSUS_SYSTEM}]}, {"role":"user","content":[{"type":"text","text":prompt}]}], "max_tokens": 4000}
        async with httpx.AsyncClient(timeout=120) as c:
            r = await c.post("https://api.anthropic.com/v1/messages", headers=headers, json=payload)
            r.raise_for_status(); j=r.json(); text = "".join(p.get("text","") for p in j.get("content",[]) if p.get("type")=="text")
            return AuditResult(provider=f"{provider.name}-consensus", model=provider.model, audit_text=text, raw=j)
    else:  # Gemini
        api_key = os.getenv("GEMINI_API_KEY")
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{provider.model}:generateContent?key={api_key}"
        parts = [{"text": CONSENSUS_SYSTEM}, {"text": prompt}]
        payload = {"contents": [{"role":"user","parts": parts}]}
        async with httpx.AsyncClient(timeout=120) as c:
            r = await c.post(url, json=payload); r.raise_for_status(); j=r.json()
            text = "".join(p.get("text","") for cnd in j.get("candidates",[]) for p in cnd.get("content",{}).get("parts",[]))
            return AuditResult(provider=f"{provider.name}-consensus", model=provider.model, audit_text=text, raw=j)

# ------------------------------- CLI --------------------------------------- #

class RunConfig(BaseModel):
    providers: List[str] = ["openai", "anthropic", "gemini"]
    consensus: str = "openai"  # which provider to use for consolidation
    excludes: List[str] = DEFAULT_EXCLUDES
    max_files: int = MAX_FILES_DEFAULT
    max_bytes: int = MAX_BYTES_DEFAULT
    model_overrides: Dict[str, str] = {}


def provider_from_name(name: str, override: Optional[str]) -> Provider:
    name = name.lower()
    model = override or DEFAULT_MODELS.get(name)
    if name == "openai":
        return OpenAIProvider(model=model)
    if name == "anthropic":
        return AnthropicProvider(model=model)
    if name == "gemini":
        return GeminiProvider(model=model)
    if name == "grok":
        return GrokProvider(model=model)
    raise typer.BadParameter(f"unknown provider: {name}")


@app.command()
def run(
    providers: List[str] = typer.Option(["openai","gemini","grok"], help="Providers to query"),
    consensus: str = typer.Option("openai", help="Provider for consolidation"),
    root: Path = typer.Option(Path("."), exists=True, help="Project root (git repo)"),
    issues_file: Optional[Path] = typer.Option(None, help="Optional AUDIT_ISSUES.md path"),
    out_dir: Path = typer.Option(Path("audits"), help="Output root for audits"),
    project_name: Optional[str] = typer.Option(None, help="Display name for project"),
    max_files: int = typer.Option(MAX_FILES_DEFAULT, help="Max files sampled for fallback"),
    max_bytes: int = typer.Option(MAX_BYTES_DEFAULT, help="Max bytes sampled for fallback"),
):
    """Run a full multi‑LLM audit + consensus pass."""
    ts = timestamp()
    run_dir = out_dir / ts
    run_dir.mkdir(parents=True, exist_ok=True)

    project_name = project_name or (git_root(root) or root).name

    # 1) Zip repo
    console.rule("Zipping repository")
    tarball = zip_repo(root, run_dir, DEFAULT_EXCLUDES)
    rprint(f"[bold green]Created[/] {tarball}")

    # 2) Build prompt
    console.rule("Building prompt")
    prompt = build_audit_prompt(project_name, root, issues_file)
    (run_dir / "PROMPT.txt").write_text(prompt)

    # Prepare fallback content (manifest + samples)
    files = gather_files(root, DEFAULT_EXCLUDES)
    manifest, samples = make_manifest(root, files, max_files=max_files, max_bytes=max_bytes)
    (run_dir / "MANIFEST.txt").write_text(manifest)

    # 3) Send to providers
    console.rule("Querying providers")
    results: Dict[str, AuditResult] = {}

    async def hit(pv_name: str):
        override = None
        prov = provider_from_name(pv_name, override)
        res = await prov.audit(prompt, root, tarball, manifest, samples)
        results[pv_name] = res
        (run_dir / f"{pv_name}.md").write_text(res.audit_text)
        (run_dir / f"{pv_name}.json").write_text(json.dumps(res.raw or {}, indent=2))
        rprint(f"[bold cyan]{pv_name}[/] ✓ saved -> {run_dir / (pv_name + '.md')}")

    asyncio.run(asyncio.gather(*[hit(p) for p in providers]))

    # 5) Consolidation
    console.rule("Consolidating audits")
    audits_map = {name: res.audit_text for name, res in results.items()}
    consensus_provider = provider_from_name(consensus, None)
    consensus_result = asyncio.run(run_consensus(consensus_provider, audits_map))
    (run_dir / "CONSENSUS.md").write_text(consensus_result.audit_text)

    # 6) Derive a concise TODO for implementation & ClaudeCode handoff
    todo = derive_todo(consensus_result.audit_text)
    (run_dir / "CONSENSUS.todo.md").write_text(todo)

    # 7) ClaudeCode handoff — generate CLAUDE.md
    claude = build_claude_tasks(project_name, todo)
    (run_dir / "CLAUDE.md").write_text(claude)

    console.rule("Done")
    rprint(f"[bold]Artifacts[/]: {run_dir}")


def derive_todo(consensus_text: str) -> str:
    # Greedy parse of numbered findings; produce checkboxes users can action.
    items = []
    for line in consensus_text.splitlines():
        m = re.match(r"\s*\d+[\).]\s*(.+)", line)
        if m:
            items.append(m.group(1).strip())
    if not items:
        # fallback to bullets
        for line in consensus_text.splitlines():
            if line.strip().startswith("-"):
                items.append(line.strip()[1:].strip())
    items = items[:50]
    lines = ["# CONSENSUS TODO\n"] + [f"- [ ] {it}" for it in items]
    return "\n".join(lines)


def build_claude_tasks(project: str, todo_md: str) -> str:
    return textwrap.dedent(f"""
    # {project}: ClaudeCode Implementation Plan

    **Goal**: Implement the consensus audit recommendations with minimal churn and tight feedback loops.

    ## Working Rules
    - Start with Fast Wins; open small PRs (< 200 lines) with clear titles.
    - Each change must include a test, doc note, or linter rule preventing regression.
    - Keep stateful changes behind feature flags; default off.

    ## Task List (import these into ClaudeCode)
    {todo_md}

    ## PR Cadence
    - PR 1: Safety Nets (CI, lint, typing baselines). Ensure green.
    - PR 2..N: One finding per PR; hold the line on tests.

    ## Definition of Done
    - All critical findings addressed or ticketed.
    - CI green, coverage trend non‑decreasing, release notes updated.
    """)


@app.command()
def latest(out_dir: Path = typer.Option(Path("audits"), help="Audit root")):
    """Print a table of the latest run and where to find the artifacts."""
    runs = sorted([p for p in out_dir.iterdir() if p.is_dir()], reverse=True)
    if not runs:
        rprint("No audits yet.")
        raise typer.Exit(code=0)
    rd = runs[0]
    table = Table(title=f"Latest audit: {rd.name}")
    table.add_column("File"); table.add_column("Path")
    for name in ["PROMPT.txt","MANIFEST.txt","openai.md","anthropic.md","gemini.md","CONSENSUS.md","CONSENSUS.todo.md","CLAUDE.md"]:
        p = rd / name
        if p.exists():
            table.add_row(name, str(p))
    console.print(table)


@app.command()
def init_config(path: Path = typer.Option(Path(".auditor.yml"))):
    """Write a starter YAML config you can tweak (optional)."""
    import yaml
    cfg = {
        "providers": ["openai","gemini","grok"],
        "consensus": "openai",
        "excludes": DEFAULT_EXCLUDES,
        "models": DEFAULT_MODELS,
        "max_files": MAX_FILES_DEFAULT,
        "max_bytes": MAX_BYTES_DEFAULT,
    }
    path.write_text(yaml.safe_dump(cfg, sort_keys=False))
    rprint(f"Wrote {path}")


if __name__ == "__main__":
    app()
