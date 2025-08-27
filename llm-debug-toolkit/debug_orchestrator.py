#!/usr/bin/env python3
"""
LLM Debug Toolkit - Multi‑LLM Interactive Debugging Assistant
Reusable package for conducting interactive debugging sessions using multiple LLM providers.

Quick start:
  export OPENAI_API_KEY=...      # if using OpenAI
  export ANTHROPIC_API_KEY=...   # if using Anthropic
  export GEMINI_API_KEY=...      # if using Google Gemini
  export XAI_API_KEY=...         # if using Grok (xAI)

  pip install -r requirements.txt
  python debug_orchestrator.py debug --providers openai gemini grok --consensus openai
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
from rich.prompt import Prompt, Confirm
from rich.panel import Panel
from rich.markdown import Markdown

try:
    import tiktoken  # token estimation for chunking fallback
except Exception:
    tiktoken = None

app = typer.Typer(add_completion=False, help="Multi‑LLM interactive debugging CLI")
console = Console()

# ------------------------------- Config ------------------------------------ #

# Enhanced exclusion patterns for broad project compatibility
DEFAULT_EXCLUDES = [
    # Version control and CI/CD
    ".git", ".github", ".gitlab", ".gitignore", ".gitmodules",
    
    # Build artifacts and dependencies
    "target", "build", "dist", "out", "__pycache__", "*.pyc",
    "node_modules", "bower_components", "jspm_packages",
    
    # Java/Maven/Gradle
    "*.class", "*.jar", "*.war", "*.ear", ".mvn", "gradle",
    
    # .NET
    "bin", "obj", "packages", "*.dll", "*.exe",
    
    # Python
    ".venv", "venv", "env", ".env", "*.egg-info", ".pytest_cache",
    "__pycache__", "*.pyc", "*.pyo", "*.pyd",
    
    # JavaScript/TypeScript
    "node_modules", ".npm", ".yarn", "dist", "build",
    
    # IDEs and editors
    ".idea", ".vscode", ".sublime-text", "*.swp", "*.swo", "*~",
    
    # OS files
    ".DS_Store", "Thumbs.db", "desktop.ini",
    
    # Logs and temp files
    "*.log", "*.tmp", "*.temp", "logs",
    
    # Package/app bundles (common large directories)
    "*.app", "*.exe", "*.dmg", "*.pkg", "*.deb", "*.rpm",
    
    # Documentation builds
    "_site", "site", "docs/_build", "docs/build",
    
    # Test coverage
    "coverage", ".coverage", "htmlcov", ".nyc_output",
    
    # Databases
    "*.sqlite", "*.db", "*.sqlite3",
]

DEFAULT_MODELS = {
    "openai": "gpt-4o",                    # Best for complex debugging
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
            ".class", ".jar", ".war", ".ear", ".dll", ".exe", ".so", ".dylib",
        }
    except Exception:
        return False


def should_exclude(path: Path, excludes: List[str]) -> bool:
    s = str(path)
    path_parts = s.split(os.sep)
    
    for ex in excludes:
        if ex.startswith("*"):
            if path.match(ex):
                return True
        else:
            # Check if any part of the path matches the exclusion
            if ex in path_parts:
                return True
            # Also check for substring matches in filename for extensions
            if ex.startswith(".") and path.name.endswith(ex):
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
    zip_path = out_dir / f"debug_repo_{timestamp()}.tar.gz"
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
            ".md": 6, ".txt": 6, ".xml": 5, ".sql": 5, ".sh": 5, ".bat": 5,
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

# ------------------------- Interactive Prompt Builder ---------------------- #

def show_prompt_guidelines():
    """Display prompt engineering guidelines for debugging."""
    guidelines = """
# 🐛 Debugging Prompt Engineering Guidelines

## Essential Components for Effective Debugging Prompts:

### 1. **Clear Problem Statement**
   - What is the expected behavior?
   - What is the actual behavior?
   - When does the issue occur?

### 2. **Reproduction Steps**
   - Specific steps to reproduce the bug
   - Input data or conditions that trigger it
   - Environment details (OS, versions, etc.)

### 3. **Component Focus**
   - Which files/modules are likely involved?
   - What system components interact?
   - Any recent changes to related code?

### 4. **Error Information**
   - Exact error messages or stack traces
   - Log entries related to the issue
   - Any diagnostic output

### 5. **Context & Constraints**
   - Timeline pressure (urgent fix vs thorough analysis)
   - Risk tolerance (safe incremental fix vs major refactor)
   - Dependencies that cannot be changed

## 📝 Example Good Debug Prompt:

"The user login process fails intermittently (works ~70% of the time) when users click 
'Sign In' after entering valid credentials. Expected: redirect to dashboard. Actual: 
shows generic 'Authentication failed' message. 

Error occurs mainly during peak hours (2-4 PM EST). Stack trace shows timeout in 
UserService.authenticate() line 45. 

Likely components: AuthController.java, UserService.java, database connection pool.
Recent changes: Added rate limiting last week in middleware.

Timeline: Need quick fix by EOD, full solution can wait for next sprint."

## ❌ Avoid Vague Prompts Like:
- "My app is broken"  
- "Login doesn't work"
- "Fix the bugs"
- "Make it faster"
"""
    
    console.print(Panel(Markdown(guidelines), title="🔧 Debug Prompt Engineering", border_style="blue"))


def collect_debug_prompt() -> str:
    """Interactive prompt collection with guidance."""
    console.print("\n[bold blue]🐛 LLM Debug Toolkit - Interactive Prompt Builder[/bold blue]\n")
    
    # Show guidelines
    if Confirm.ask("📚 Would you like to see prompt engineering guidelines first?", default=True):
        show_prompt_guidelines()
        console.print()
    
    console.print("[bold]Let's build your debugging prompt step by step:[/bold]\n")
    
    # Collect components step by step
    problem_statement = Prompt.ask(
        "[yellow]1. Problem Statement[/yellow]\n"
        "   Describe the issue: What should happen vs what actually happens?"
    )
    
    reproduction_steps = Prompt.ask(
        "\n[yellow]2. Reproduction Steps[/yellow]\n"
        "   How can the issue be reproduced? Include specific steps, inputs, conditions:"
    )
    
    components = Prompt.ask(
        "\n[yellow]3. Likely Components[/yellow]\n"
        "   Which files, modules, or system parts are probably involved?"
    )
    
    error_info = Prompt.ask(
        "\n[yellow]4. Error Information[/yellow]\n"
        "   Any error messages, stack traces, or log entries? (Enter 'none' if not applicable)",
        default="none"
    )
    
    context = Prompt.ask(
        "\n[yellow]5. Context & Constraints[/yellow]\n"
        "   Timeline, risk tolerance, dependencies, recent changes? (Enter 'none' if not applicable)",
        default="none"
    )
    
    # Allow free-form addition
    additional = Prompt.ask(
        "\n[yellow]6. Additional Information[/yellow]\n"
        "   Anything else relevant? Environment details, suspicious patterns, etc.? (Enter 'none' if not applicable)",
        default="none"
    )
    
    # Build structured prompt
    prompt_parts = [
        "🐛 **DEBUG REQUEST**",
        "",
        "## Problem Statement",
        problem_statement,
        "",
        "## Reproduction Steps", 
        reproduction_steps,
        "",
        "## Likely Components",
        components,
    ]
    
    if error_info.lower() != "none":
        prompt_parts.extend(["", "## Error Information", error_info])
    
    if context.lower() != "none":
        prompt_parts.extend(["", "## Context & Constraints", context])
    
    if additional.lower() != "none":
        prompt_parts.extend(["", "## Additional Information", additional])
    
    user_prompt = "\n".join(prompt_parts)
    
    # Show preview and confirm
    console.print("\n[bold green]📝 Your Debug Prompt Preview:[/bold green]")
    console.print(Panel(Markdown(user_prompt), border_style="green"))
    
    if Confirm.ask("\n✅ Does this look good?", default=True):
        return user_prompt
    else:
        console.print("[yellow]Let's try again...[/yellow]\n")
        return collect_debug_prompt()  # Recursive retry


def build_debug_prompt(project_name: str, root: Path, user_debug_prompt: str) -> str:
    """Build the final prompt that combines user input with technical context."""
    git_log = recent_git_context(root, 15)  # Shorter for debugging focus
    readme = (root / "README.md").read_text()[:8000] if (root / "README.md").exists() else ""
    
    prompt = f"""
You are a senior software engineer and debugging expert. Help debug the issue described below in the repository "{project_name}".

## USER'S DEBUG REQUEST:
{user_debug_prompt}

## DEBUGGING APPROACH:
1) **Root Cause Analysis**: Identify the most likely cause(s) of the described issue
2) **Code Investigation**: Examine relevant files and logic flows that could be responsible
3) **Reproduction Strategy**: Suggest specific ways to isolate and reproduce the issue
4) **Solution Options**: Provide multiple fix approaches (quick fix vs comprehensive solution)
5) **Testing Strategy**: Recommend how to verify the fix and prevent regression
6) **Related Issues**: Identify any similar issues that might exist in the codebase

## REPOSITORY CONTEXT:

### Recent Git Activity (last 15 commits):
{git_log}

### Project README (excerpt):
{readme}

## DELIVERABLES:
- **Root Cause Analysis** — most likely explanations for the issue
- **Investigation Plan** — which files/components to examine first, debugging steps  
- **Reproduction Guide** — concrete steps to isolate the issue
- **Solution Options** — quick fixes vs comprehensive solutions with trade-offs
- **Implementation Steps** — detailed code changes needed
- **Testing Strategy** — how to verify the fix works
- **Prevention** — how to avoid similar issues in the future

Focus on being practical and actionable. Cite specific files and line numbers when possible.
If multiple root causes are possible, rank them by likelihood and suggest how to determine which is correct.
""".strip()
    return prompt

# ---------------------------- Provider Base -------------------------------- #

class DebugResult(BaseModel):
    provider: str
    model: str
    debug_analysis: str
    raw: Optional[Dict[str, Any]] = None

class Provider:
    name: str
    model: str

    def __init__(self, model: Optional[str] = None):
        self.model = model or DEFAULT_MODELS.get(self.name, "")

    async def debug(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> DebugResult:
        raise NotImplementedError

# ----------------------------- OpenAI -------------------------------------- #

class OpenAIProvider(Provider):
    name = "openai"

    async def debug(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> DebugResult:
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
                        "instructions": "You are a debugging expert. Analyze the codebase to help solve the described issue.",
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
                return DebugResult(provider=self.name, model=self.model, debug_analysis=text, raw=data)
            except Exception as e:
                # Fallback to chat.completions with manifest+samples
                headers = {"Authorization": f"Bearer {api_key}"}
                content = [{"type":"text", "text": prompt}, {"type":"text", "text": "\nFALLBACK MODE: Repository manifest and key files below."}, {"type":"text", "text": manifest}]
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
                    return DebugResult(provider=self.name, model=self.model, debug_analysis=text, raw=j)

# ----------------------------- Anthropic ----------------------------------- #

class AnthropicProvider(Provider):
    name = "anthropic"

    async def debug(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> DebugResult:
        api_key = os.getenv("ANTHROPIC_API_KEY")
        if not api_key:
            raise RuntimeError("ANTHROPIC_API_KEY not set")
        headers = {"x-api-key": api_key, "anthropic-version": "2023-06-01"}
        # Newer betas support file attachments; we provide a robust fallback.
        content: List[Dict[str, Any]] = [{"type": "text", "text": prompt}]
        # Attach manifest and a limited set of samples
        content.append({"type":"text","text":"\nFALLBACK MODE: Repository manifest and key files below."})
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
            return DebugResult(provider=self.name, model=self.model, debug_analysis=text, raw=j)

# ------------------------------- Gemini ------------------------------------ #

class GeminiProvider(Provider):
    name = "gemini"

    async def debug(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> DebugResult:
        api_key = os.getenv("GEMINI_API_KEY")
        if not api_key:
            raise RuntimeError("GEMINI_API_KEY not set")
        # Use Generative Language API v1beta; include manifest + samples as text parts
        # For very large repos, you can switch to the file API and provide a file‑uri here.
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{self.model}:generateContent?key={api_key}"
        parts = [{"text": prompt}, {"text": "\nFALLBACK MODE: Repository manifest and key files below."}, {"text": manifest}]
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
            return DebugResult(provider=self.name, model=self.model, debug_analysis=text, raw=j)

# ------------------------------- Grok (xAI) -------------------------------- #

class GrokProvider(Provider):
    name = "grok"

    async def debug(self, prompt: str, root: Path, tarball: Path, manifest: str, samples: List[Tuple[Path, bytes]] ) -> DebugResult:
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
            {"role": "system", "content": "You are a precise debugging expert. Analyze code systematically to find root causes and provide actionable solutions."},
            {"role": "user", "content": prompt + "\n\nFALLBACK MODE: Repository manifest and key files below.\n" + manifest}
        ]
        # Append a few sampled files as extra messages to aid grounding.
        for rel, blob in samples[:25]:
            with contextlib.suppress(Exception):
                snippet = blob.decode(errors="ignore")[:15000]
                content.append({"role": "user", "content": f"===== {rel} =====\n{snippet}"})
        payload = {"model": self.model, "messages": content, "temperature": 0.1}  # Lower temp for debugging precision
        async with httpx.AsyncClient(timeout=120) as client:
            resp = await client.post("https://api.x.ai/v1/chat/completions", headers=headers, json=payload)
            resp.raise_for_status()
            j = resp.json()
            text = j.get("choices", [{}])[0].get("message", {}).get("content", "")
            return DebugResult(provider=self.name, model=self.model, debug_analysis=text or "(No text returned)", raw=j)

# ---------------------------- Consolidation -------------------------------- #

CONSENSUS_SYSTEM = (
    "You are an experienced tech lead consolidating multiple debugging analyses into a single, actionable plan.\n"
    "Synthesize the different perspectives to create a coherent debugging strategy with clear next steps."
)


def build_consensus_prompt(analyses: Dict[str, str]) -> str:
    blobs = []
    for name, text in analyses.items():
        blobs.append(f"===== DEBUG ANALYSIS: {name} =====\n{text}\n")
    tail = """
Required output:
1) Consensus Root Cause Assessment (most likely cause based on all analyses)
2) Unified Investigation Plan (step-by-step debugging approach)
3) Solution Recommendations (quick fix + long-term solution with implementation steps)
4) Testing & Validation Strategy
5) Risk Assessment & Rollback Plan
6) Prevention Measures (how to avoid similar issues)

Where analyses disagree, explain the reasoning for your final recommendation.
Focus on practical next steps the developer can take immediately.
""".strip()
    return "\n".join(blobs + [tail])

async def run_consensus(provider: Provider, analyses: Dict[str, str]) -> DebugResult:
    prompt = build_consensus_prompt(analyses)
    # Reuse provider with a simple text‑only message
    if isinstance(provider, OpenAIProvider):
        api_key = os.getenv("OPENAI_API_KEY"); headers = {"Authorization": f"Bearer {api_key}"}
        payload = {"model": provider.model, "messages": [{"role":"system","content": CONSENSUS_SYSTEM}, {"role":"user","content": prompt}]}
        async with httpx.AsyncClient(timeout=120) as c:
            r = await c.post("https://api.openai.com/v1/chat/completions", headers=headers, json=payload)
            r.raise_for_status()
            j = r.json(); text = j["choices"][0]["message"]["content"]
            return DebugResult(provider=f"{provider.name}-consensus", model=provider.model, debug_analysis=text, raw=j)
    elif isinstance(provider, AnthropicProvider):
        api_key = os.getenv("ANTHROPIC_API_KEY"); headers={"x-api-key": api_key, "anthropic-version":"2023-06-01"}
        payload = {"model": provider.model, "messages": [{"role":"system","content":[{"type":"text","text":CONSENSUS_SYSTEM}]}, {"role":"user","content":[{"type":"text","text":prompt}]}], "max_tokens": 4000}
        async with httpx.AsyncClient(timeout=120) as c:
            r = await c.post("https://api.anthropic.com/v1/messages", headers=headers, json=payload)
            r.raise_for_status(); j=r.json(); text = "".join(p.get("text","") for p in j.get("content",[]) if p.get("type")=="text")
            return DebugResult(provider=f"{provider.name}-consensus", model=provider.model, debug_analysis=text, raw=j)
    else:  # Gemini
        api_key = os.getenv("GEMINI_API_KEY")
        url = f"https://generativelanguage.googleapis.com/v1beta/models/{provider.model}:generateContent?key={api_key}"
        parts = [{"text": CONSENSUS_SYSTEM}, {"text": prompt}]
        payload = {"contents": [{"role":"user","parts": parts}]}
        async with httpx.AsyncClient(timeout=120) as c:
            r = await c.post(url, json=payload); r.raise_for_status(); j=r.json()
            text = "".join(p.get("text","") for cnd in j.get("candidates",[]) for p in cnd.get("content",{}).get("parts",[]))
            return DebugResult(provider=f"{provider.name}-consensus", model=provider.model, debug_analysis=text, raw=j)

# ------------------------------- CLI --------------------------------------- #

class RunConfig(BaseModel):
    providers: List[str] = ["openai", "gemini", "grok"]
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
def debug(
    providers: List[str] = typer.Option(["openai","gemini","grok"], help="Providers to query"),
    consensus: str = typer.Option("openai", help="Provider for consolidation"),
    root: Path = typer.Option(Path("."), exists=True, help="Project root (git repo)"),
    out_dir: Path = typer.Option(Path("debug_sessions"), help="Output root for debug sessions"),
    project_name: Optional[str] = typer.Option(None, help="Display name for project"),
    max_files: int = typer.Option(MAX_FILES_DEFAULT, help="Max files sampled for fallback"),
    max_bytes: int = typer.Option(MAX_BYTES_DEFAULT, help="Max bytes sampled for fallback"),
):
    """Run an interactive debugging session with multiple LLM providers."""
    ts = timestamp()
    run_dir = out_dir / ts
    run_dir.mkdir(parents=True, exist_ok=True)

    project_name = project_name or (git_root(root) or root).name

    # Collect user's debug prompt interactively
    console.rule("Interactive Debug Prompt Collection")
    user_debug_prompt = collect_debug_prompt()
    (run_dir / "USER_PROMPT.md").write_text(user_debug_prompt)

    # 1) Zip repo
    console.rule("Packaging repository")
    tarball = zip_repo(root, run_dir, DEFAULT_EXCLUDES)
    rprint(f"[bold green]Created[/] {tarball}")

    # 2) Build final prompt
    console.rule("Building debug prompt")
    prompt = build_debug_prompt(project_name, root, user_debug_prompt)
    (run_dir / "FINAL_PROMPT.txt").write_text(prompt)

    # Prepare fallback content (manifest + samples)
    files = gather_files(root, DEFAULT_EXCLUDES)
    manifest, samples = make_manifest(root, files, max_files=max_files, max_bytes=max_bytes)
    (run_dir / "MANIFEST.txt").write_text(manifest)

    # 3) Send to providers
    console.rule("Querying debug providers")
    results: Dict[str, DebugResult] = {}

    async def hit(pv_name: str):
        override = None
        prov = provider_from_name(pv_name, override)
        res = await prov.debug(prompt, root, tarball, manifest, samples)
        results[pv_name] = res
        (run_dir / f"{pv_name}.md").write_text(res.debug_analysis)
        (run_dir / f"{pv_name}.json").write_text(json.dumps(res.raw or {}, indent=2))
        rprint(f"[bold cyan]{pv_name}[/] ✓ saved -> {run_dir / (pv_name + '.md')}")

    async def run_all_providers():
        await asyncio.gather(*[hit(p) for p in providers])
    
    asyncio.run(run_all_providers())

    # 5) Consolidation
    console.rule("Creating consensus debug plan")
    analyses_map = {name: res.debug_analysis for name, res in results.items()}
    consensus_provider = provider_from_name(consensus, None)
    consensus_result = asyncio.run(run_consensus(consensus_provider, analyses_map))
    (run_dir / "CONSENSUS.md").write_text(consensus_result.debug_analysis)

    # 6) Create action items
    action_items = derive_action_items(consensus_result.debug_analysis)
    (run_dir / "ACTION_ITEMS.md").write_text(action_items)

    # 7) Debug session summary
    summary = build_debug_summary(project_name, user_debug_prompt, action_items)
    (run_dir / "DEBUG_SUMMARY.md").write_text(summary)

    console.rule("Debug Session Complete")
    rprint(f"[bold]Debug session artifacts[/]: {run_dir}")
    
    # Show quick summary
    console.print("\n[bold green]🎯 Quick Summary:[/bold green]")
    console.print(f"Debug session saved to: [cyan]{run_dir}[/cyan]")
    console.print(f"Key files: [yellow]CONSENSUS.md[/yellow], [yellow]ACTION_ITEMS.md[/yellow], [yellow]DEBUG_SUMMARY.md[/yellow]")


def derive_action_items(consensus_text: str) -> str:
    """Extract actionable items from consensus analysis."""
    items = []
    in_steps = False
    
    for line in consensus_text.splitlines():
        # Look for numbered steps or bullet points
        line = line.strip()
        if re.match(r'\d+[\)\.]\s', line):
            items.append(re.sub(r'^\d+[\)\.]\s*', '', line))
        elif line.startswith('- ') or line.startswith('* '):
            items.append(line[2:])
        elif 'step' in line.lower() and ':' in line:
            items.append(line.split(':', 1)[1].strip())
        elif line.lower().startswith('todo') or line.lower().startswith('action'):
            items.append(line)
    
    if not items:
        # Fallback: extract sentences that sound like actions
        sentences = consensus_text.replace('\n', ' ').split('.')
        for sentence in sentences:
            if any(word in sentence.lower() for word in ['check', 'test', 'modify', 'add', 'remove', 'fix', 'verify', 'examine']):
                items.append(sentence.strip())
    
    items = items[:20]  # Limit to top 20 items
    lines = ["# Debug Action Items\n"] + [f"- [ ] {item}" for item in items if item.strip()]
    return "\n".join(lines)


def build_debug_summary(project: str, user_prompt: str, action_items: str) -> str:
    """Build a concise debug session summary."""
    return textwrap.dedent(f"""
    # 🐛 Debug Session Summary: {project}
    
    **Session Date**: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}
    
    ## Original Issue
    {user_prompt[:500]}{'...' if len(user_prompt) > 500 else ''}
    
    ## Next Steps
    {action_items}
    
    ## Session Files
    - **USER_PROMPT.md** - Original debug request
    - **CONSENSUS.md** - Unified analysis from all providers
    - **ACTION_ITEMS.md** - Concrete next steps checklist
    - **Individual analyses** - openai.md, gemini.md, grok.md, etc.
    
    ## Follow-up
    After implementing fixes:
    1. Test the solution thoroughly
    2. Run regression tests
    3. Consider running another debug session if issues persist
    4. Document the solution for future reference
    """)


@app.command()
def latest(out_dir: Path = typer.Option(Path("debug_sessions"), help="Debug sessions root")):
    """Print a table of the latest debug session."""
    sessions = sorted([p for p in out_dir.iterdir() if p.is_dir()], reverse=True)
    if not sessions:
        rprint("No debug sessions yet.")
        raise typer.Exit(code=0)
    rd = sessions[0]
    table = Table(title=f"Latest debug session: {rd.name}")
    table.add_column("File"); table.add_column("Path")
    for name in ["USER_PROMPT.md","FINAL_PROMPT.txt","CONSENSUS.md","ACTION_ITEMS.md","DEBUG_SUMMARY.md","openai.md","gemini.md","grok.md"]:
        p = rd / name
        if p.exists():
            table.add_row(name, str(p))
    console.print(table)


@app.command()
def init_config(path: Path = typer.Option(Path(".debug_config.yml"))):
    """Write a starter YAML config for debug sessions."""
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