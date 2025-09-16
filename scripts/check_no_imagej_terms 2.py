#!/usr/bin/env python3
import sys, os, re
from pathlib import Path

# Terms that indicate an ImageJ/Fiji/Java regression.
PATTERNS = [
    r"\bImageJ\b",
    r"\bFiji\b",
    r"net\.imagej",
    r"scijava",
    r"scifio",
    r"loci\.formats",
    r"\bBio-Formats\b",
    r"bioformats",
    r"(?<![A-Za-z])IJ\.",   # IJ.run, IJ.* style calls
    r"\.ijm\b",
    r"Fiji\.app"
]
RX = [re.compile(p, re.IGNORECASE) for p in PATTERNS]

# Directories and files to ignore (safe legacy quarantine + common vendor dirs)
IGNORE_DIRS = {
    ".git", ".hg", ".svn", ".mypy_cache", ".pytest_cache",
    "node_modules", "dist", "build", "__pycache__", ".venv", "venv",
    "autodense/legacy/legacy_macros", "autodense/legacy/translated", "docs", "NextSteps", "audits", "SessionSummaries", "StructureDocs", "autodense/packaging"
}
# Files explicitly allowed to mention the terms (migration docs, changelogs)
ALLOW_FILES = {"MIGRATION_REPORT.md", "README_mask_ops.md", "README_codemod.md", "DELETION_CANDIDATES.txt", "README_precommit_policy.md", ".pre-commit-config.yaml", "cspell.json", "Architecture.md", "Makefile", "docs_inventory.csv", "PROJECT_STRUCTURE_REFERENCE.md", "autodense/README.md"}

# Extensions we consider text (skip obvious binaries)
TEXT_EXTS = {
    ".py",".md",".rst",".txt",".yml",".yaml",".toml",".cfg",".ini",".json",
    ".sh",".bat",".ps1",".ts",".tsx",".js",".jsx",".html",".css",".csv"
}

MAX_FILE_MB = 2

def is_ignored(path: Path) -> bool:
    p = str(path).replace("\\", "/")
    for d in IGNORE_DIRS:
        if f"/{d}/" in f"/{p}/" or p == d or p.endswith("/"+d):
            return True
    return False

def is_text_file(path: Path) -> bool:
    if path.suffix.lower() in TEXT_EXTS:
        return True
    # tiny heuristic fallback
    try:
        with open(path, "rb") as f:
            chunk = f.read(8000)
        if b"\x00" in chunk:
            return False
        return True
    except Exception:
        return False

def scan_paths(paths_to_scan, base_path: Path) -> list:
    """Scan specific paths instead of entire repo"""
    violations = []
    for target in paths_to_scan:
        path = Path(target).resolve()
        if not path.exists():
            continue
        if path.is_file():
            files = [path]
        else:
            files = list(path.rglob("*"))
        
        for filepath in files:
            if not filepath.is_file():
                continue
            if is_ignored(filepath):
                continue
            if filepath.name in ALLOW_FILES:
                continue
            if filepath.stat().st_size > MAX_FILE_MB * 1024 * 1024:
                continue
            if not is_text_file(filepath):
                continue
            try:
                text = filepath.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            for i, line in enumerate(text.splitlines(), start=1):
                for rx in RX:
                    if rx.search(line):
                        snippet = line.strip()
                        rel_path = str(filepath.relative_to(base_path)) if filepath.is_relative_to(base_path) else str(filepath)
                        violations.append((rel_path, i, rx.pattern, snippet[:200]))
    return violations

def main(repo_root: str = None, specific_paths: list = None) -> int:
    if specific_paths:
        base_path = Path(repo_root or os.getcwd()).resolve()
        violations = scan_paths(specific_paths, base_path)
    else:
        repo = Path(repo_root or os.getcwd()).resolve()
        violations = []
        for path in repo.rglob("*"):
            if not path.is_file():
                continue
            if is_ignored(path):
                continue
            if path.name in ALLOW_FILES:
                continue
            if path.stat().st_size > MAX_FILE_MB * 1024 * 1024:
                continue
            if not is_text_file(path):
                continue
            try:
                text = path.read_text(encoding="utf-8", errors="ignore")
            except Exception:
                continue
            for i, line in enumerate(text.splitlines(), start=1):
                for rx in RX:
                    if rx.search(line):
                        snippet = line.strip()
                        violations.append((str(path.relative_to(repo)), i, rx.pattern, snippet[:200]))
    if violations:
        print("✖ Policy violation: ImageJ/Fiji/Java-era terms detected.\n")
        for file, line, pat, snip in violations[:200]:
            print(f"- {file}:{line}: {pat}\n    {snip}")
        if len(violations) > 200:
            print(f"... and {len(violations)-200} more matches")
        print("\nIf this mention is intentional, move the file to a legacy folder or add it to ALLOW_FILES above.")
        return 1
    print("✓ Policy OK: no ImageJ/Fiji/Java-era terms found.")
    return 0

if __name__ == "__main__":
    if len(sys.argv) > 1:
        # Called with specific paths to check
        sys.exit(main(specific_paths=sys.argv[1:]))
    else:
        # Default behavior: scan entire repo
        sys.exit(main())
