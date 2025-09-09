#!/usr/bin/env python3
"""
AutoDense Codebase Packaging Script

Creates tar.gz archive of AutoDense source code for audit, review, or distribution.
Optimized to exclude large files that are not necessary for understanding AutoDense functionality.

Major exclusions to reduce package size from ~2GB to ~50MB:
- Generated output directories: out/ (381MB), preclass_out/ (424MB), output/ (45MB)
- Sample/seed image collections: SeedImages/ (224MB), autodense/samples/ (12MB) 
- Packaging resources: autodense/packaging/ (322MB with JARs and app bundles)
- Python virtual environment: .venv/ (with 632MB+ TensorFlow libraries)
- Documentation files: *.md, *.pdf, *.txt (can be regenerated)
- Build artifacts: *.jar, *.class, target/, __pycache__/
- Large binaries: *.dylib, *.so (native libraries)
- Previous archives: *.tar.gz, *.zip

The resulting package contains only essential source code for understanding and modifying AutoDense.

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

    # Image files
    "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp", "*.tiff", "*.webp","*.heic","*.tif","*.HEIC","*.TIFF","*.JPG","*.JPEG"
    
    # Documents
    "*.md","*.MD","*.pdf","*.PDF","*.doc","*.DOC","*.docx","*.DOCX","*.ppt","*.PPT","*.pptx","*.PPTX","*.xls","*.XLS","*.xlsx","*.XLSX","*.csv","*.CSV","*.txt","*.TXT"
   
    # Package/app bundles (AutoDense specific)
    "packaging/resources/AutoDense.app",
    "packaging/resources/Fiji.app",
    "packaging/resources/models",
    "packaging/resources/icons",
    
    # Large files not necessary for understanding functionality issues
    # ================================================================
    
    # Generated output directories (can be recreated by running tests)
    "output",  # Contains large stage0_input.png, overlay.png files (9-11MB each)
    "out",     # 381M of generated crops, QC images, and analysis results
    "preclass_out",  # 424M of preprocessing classification results
    
    # Sample and seed image directories (large collections not needed for code analysis)
    "autodense/samples",  # Large original gel photos not needed for code analysis
    "SeedImages",         # 224M of seed images for ML training (not needed for code understanding)
    "user_seed_images",   # User-specific seed images
    
    # Large dependency JARs and packaging resources (322M total)
    "packaging/resources", "autodense/packaging",  # Contains AutoDense.app bundle and JARs
    
    # Previously generated packages and archives
    "*codebase*.tar.gz",  # Avoid recursive packaging
    "*audit*.tar.gz",     # Previous audit packages
    "*.zip",              # Large zip files (like out.zip - 379M)
    
    # Large Maven/Java build artifacts
    ".m2/repository",
    "*.jar",              # Exclude JAR files (some are 178M+)
    
    # Large Python virtual environment (excluded above but being explicit)
    # .venv contains 632M+ of TensorFlow and other ML libraries
    
    # Additional large files discovered during analysis
    "*debug*toolkit*.tar.gz",  # Debug toolkit archives
    "*preflight*priors*.tar.gz",  # Preflight archives
    "*.pkl.gz",  # Python pickle files (numpy test data)
    "*.ima.gz",  # Image data files (matplotlib sample data)
    "*.dylib",   # Large dynamic libraries (72M+ each)
    "*.so",      # Shared object files (32M+ each)
]

def timestamp() -> str:
    """Generate timestamp string for file naming."""
    return datetime.now().strftime("%Y-%m-%d_%H%M%S")

def should_exclude(path: Path, excludes: List[str]) -> bool:
    """Check if a file path should be excluded from the archive."""
    try:
        path_str = str(path)
        path_parts = path_str.split(os.sep)
        
        for pattern in excludes:
            # Handle wildcard patterns (like *.md, *.jpg)
            if pattern.startswith("*"):
                if path.match(pattern):
                    return True
                # Also check just the filename for extension patterns
                if path.name.lower().endswith(pattern[1:].lower()):
                    return True
            else:
                # Handle directory/file name patterns
                if pattern in path_parts:
                    return True
                # Also check if pattern matches the full filename
                if pattern == path.name:
                    return True
                    
        return False
    except Exception as e:
        print(f"⚠️  Warning: Error checking exclusion for {path}: {e}")
        return True  # Exclude on error to be safe

def gather_files(root: Path, excludes: List[str]) -> List[Path]:
    """Gather all files from root directory, excluding specified patterns."""
    files: List[Path] = []
    excluded_count = 0
    
    if not root.exists():
        raise FileNotFoundError(f"Root directory does not exist: {root}")
    
    if not root.is_dir():
        raise NotADirectoryError(f"Root path is not a directory: {root}")
    
    try:
        for path in root.rglob("*"):
            if path.is_file():
                relative_path = path.relative_to(root)
                if should_exclude(relative_path, excludes):
                    excluded_count += 1
                    # Show a few examples of excluded files
                    if excluded_count <= 5:
                        print(f"  Excluding: {relative_path}")
                    elif excluded_count == 6:
                        print(f"  ... (and more excluded files)")
                else:
                    files.append(path)
    except Exception as e:
        raise RuntimeError(f"Error gathering files from {root}: {e}")
    
    print(f"📋 Excluded {excluded_count} files based on patterns")
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
        # Handle help request
        if len(sys.argv) > 1 and sys.argv[1] in ["--help", "-h", "help"]:
            print("Usage: python3 package_codebase.py [output_directory]")
            print("\nPackages AutoDense codebase excluding large binary files.")
            print("If no output directory is specified, creates package in project root.")
            sys.exit(0)
            
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