#!/usr/bin/env python3
"""
AutoDense Codebase Packaging Script

Creates tar.gz archive of AutoDense source code for audit, review, or distribution.
Uses the same exclusion patterns as the audit system to ensure consistent packaging.

Usage:
    python3 scripts/package_codebase.py [output_directory]

Example:
    python3 scripts/package_codebase.py ./packages
"""

import tarfile
import sys
import os
from datetime import datetime
from pathlib import Path
from typing import List, Optional

# Exclusion patterns consistent with audit_orchestrator.py
EXCLUSION_PATTERNS = [
    # Version control and CI/CD
    ".git", ".github", ".gitlab", ".gitignore",
    
    # Python environments and cache
    ".venv", "venv", "env", "__pycache__", "*.egg-info", ".pytest_cache",
    
    # Node.js
    "node_modules", "dist", "out",
    
    # Java/Maven build artifacts
    "target", "build", "*.class", "*.jar", "*.war",
    
    # IDE files
    ".idea", ".vscode", ".eclipse", "*.iml",
    
    # OS files
    ".DS_Store", "Thumbs.db",
    
    # Audit directories and temporary files
    "audits", ".audit_venv", "*.tmp", "*.temp",
    
    # External library directories (common patterns)
    "lib", "libs", "dependencies", "vendor",
    
    # Documentation build outputs
    "docs/_build", "site",
    
    # Log and cache files
    "*.log", "logs", ".cache",
    
    # Package/app bundles (AutoDense specific)
    "packaging/resources/AutoDense.app",
    "packaging/resources/Fiji.app",
    "packaging/resources/models",
    "packaging/resources/icons",
]

def timestamp() -> str:
    """Generate timestamp string for file naming."""
    return datetime.now().strftime("%Y-%m-%d_%H%M%S")

def should_exclude(path: Path, excludes: List[str]) -> bool:
    """Check if a file path should be excluded from the archive."""
    try:
        path_str = str(path)
        for pattern in excludes:
            if pattern.startswith("*"):
                if path.match(pattern):
                    return True
            else:
                if pattern in path_str.split(os.sep):
                    return True
        return False
    except Exception as e:
        print(f"⚠️  Warning: Error checking exclusion for {path}: {e}")
        return True  # Exclude on error to be safe

def gather_files(root: Path, excludes: List[str]) -> List[Path]:
    """Gather all files from root directory, excluding specified patterns."""
    files: List[Path] = []
    
    if not root.exists():
        raise FileNotFoundError(f"Root directory does not exist: {root}")
    
    if not root.is_dir():
        raise NotADirectoryError(f"Root path is not a directory: {root}")
    
    try:
        for path in root.rglob("*"):
            if path.is_file() and not should_exclude(path.relative_to(root), excludes):
                files.append(path)
    except Exception as e:
        raise RuntimeError(f"Error gathering files from {root}: {e}")
    
    return files

def create_package(root: Path, output_dir: Path, excludes: List[str]) -> Path:
    """Create tar.gz package of the codebase."""
    
    # Validate inputs
    if not root.exists():
        raise FileNotFoundError(f"Source directory does not exist: {root}")
    
    # Create output directory
    try:
        output_dir.mkdir(parents=True, exist_ok=True)
    except Exception as e:
        raise RuntimeError(f"Failed to create output directory {output_dir}: {e}")
    
    # Generate package path
    package_name = f"autodense_codebase_{timestamp()}.tar.gz"
    package_path = output_dir / package_name
    
    print(f"🔨 Packaging AutoDense codebase...")
    print(f"📁 Source: {root}")
    print(f"📦 Output: {package_path}")
    
    try:
        # Gather files first to show progress
        files = gather_files(root, excludes)
        total_files = len(files)
        
        if total_files == 0:
            raise RuntimeError("No files found to package")
        
        print(f"📋 Found {total_files} files to package")
        
        # Create the archive
        with tarfile.open(package_path, "w:gz") as tar:
            for i, file_path in enumerate(files):
                if i % 50 == 0 or i == total_files - 1:
                    progress = (i + 1) / total_files * 100
                    print(f"  Progress: {i + 1}/{total_files} files ({progress:.1f}%)")
                
                try:
                    archive_path = str(file_path.relative_to(root))
                    tar.add(file_path, arcname=archive_path)
                except Exception as e:
                    print(f"⚠️  Warning: Skipping {file_path}: {e}")
                    continue
        
        # Report results
        size_mb = package_path.stat().st_size / (1024 * 1024)
        print(f"✅ Package created successfully!")
        print(f"📦 File: {package_path}")
        print(f"💾 Size: {size_mb:.1f} MB")
        print(f"📄 Files: {total_files}")
        
        return package_path
        
    except Exception as e:
        # Clean up partial file on error
        if package_path.exists():
            try:
                package_path.unlink()
            except:
                pass
        raise RuntimeError(f"Failed to create package: {e}")

def main():
    """Main entry point for the packaging script."""
    try:
        # Determine output directory
        if len(sys.argv) > 2:
            print("Usage: python3 package_codebase.py [output_directory]")
            sys.exit(1)
        
        # Set default paths
        script_dir = Path(__file__).parent
        project_root = script_dir.parent
        output_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else project_root
        
        # Validate we're in the right place
        if not (project_root / "autodense").exists():
            raise RuntimeError(f"AutoDense project structure not found at {project_root}")
        
        # Create package
        package_path = create_package(project_root, output_dir, EXCLUSION_PATTERNS)
        
        print(f"\n🎉 AutoDense codebase packaged successfully!")
        print(f"🔍 Use this file for manual review or external audit tools.")
        
    except KeyboardInterrupt:
        print(f"\n🛑 Packaging interrupted by user")
        sys.exit(1)
    except Exception as e:
        print(f"❌ Error: {e}")
        sys.exit(1)

if __name__ == "__main__":
    main()