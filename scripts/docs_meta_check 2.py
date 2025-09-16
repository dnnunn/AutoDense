#!/usr/bin/env python3
"""
Check that all markdown documents have required Doc Meta blocks.
This enforces the documentation standards defined in CLAUDE.md.
"""
import sys, re
from pathlib import Path
from datetime import datetime, timedelta
import os

ROOT = Path(sys.argv[1] if len(sys.argv) > 1 else ".").resolve()

# Regex patterns for Doc Meta block
DOC_META_BLOCK = re.compile(r'>\s*\*\*Doc\s+Meta\*\*\s*\n((?:\s*>\s*-\s*\*\*[^:]+:\*\*.*\n?)+)', re.M | re.I)
PURPOSE = re.compile(r'>\s*-\s*\*\*Purpose:\*\*\s*(.+)', re.I)
SCOPE = re.compile(r'>\s*-\s*\*\*Scope:\*\*\s*(.+)', re.I)  
OWNER = re.compile(r'>\s*-\s*\*\*Owner:\*\*\s*(@\w+)', re.I)
LAST_VERIFIED = re.compile(r'>\s*-\s*\*\*Last-verified:\*\*\s*(\d{4}-\d{2}-\d{2})', re.I)

# Exclude certain files from meta requirements
EXCLUDED_FILES = {
    'NEW_DOC_TEMPLATE.md',
    'DEPRECATED_TEMPLATE.md'
}

def check_doc_meta(md_file: Path) -> list[str]:
    """Check a single markdown file for required Doc Meta. Returns list of errors."""
    if md_file.name in EXCLUDED_FILES:
        return []
        
    errors = []
    text = md_file.read_text(encoding='utf-8', errors='ignore')
    
    # Check for Doc Meta block
    meta_match = DOC_META_BLOCK.search(text)
    if not meta_match:
        errors.append(f"[{md_file.name}] Missing required Doc Meta block")
        return errors  # No point checking individual fields if block is missing
    
    meta_block = meta_match.group(0)
    
    # Check required fields
    if not PURPOSE.search(meta_block):
        errors.append(f"[{md_file.name}] Missing **Purpose:** in Doc Meta")
    
    if not SCOPE.search(meta_block):
        errors.append(f"[{md_file.name}] Missing **Scope:** in Doc Meta")
        
    owner_match = OWNER.search(meta_block)
    if not owner_match:
        errors.append(f"[{md_file.name}] Missing **Owner:** in Doc Meta (should be @github-handle)")
    elif owner_match.group(1) in ['@github-handle', '@your-github-handle', '@<github-handle>']:
        errors.append(f"[{md_file.name}] Owner is placeholder text, needs real GitHub handle")
    
    verified_match = LAST_VERIFIED.search(meta_block)
    if not verified_match:
        errors.append(f"[{md_file.name}] Missing **Last-verified:** in Doc Meta (should be YYYY-MM-DD)")
    else:
        try:
            verified_date = datetime.strptime(verified_match.group(1), '%Y-%m-%d').date()
            today = datetime.now().date()
            age_days = (today - verified_date).days
            
            # Warn if verification is very old (over 1 year)
            if age_days > 365:
                errors.append(f"[{md_file.name}] Last-verified is {age_days} days old (consider refresh)")
        except ValueError:
            errors.append(f"[{md_file.name}] Invalid date format in Last-verified: {verified_match.group(1)}")
    
    return errors

def main():
    """Check all markdown files for Doc Meta compliance."""
    all_errors = []
    checked_count = 0
    
    # If ROOT is a specific file, check just that file
    if ROOT.is_file() and ROOT.suffix == '.md':
        errors = check_doc_meta(ROOT)
        all_errors.extend(errors)
        checked_count = 1
    else:
        # Check all markdown files in directory
        for md_file in sorted(ROOT.rglob("*.md")):
            if ".git" in md_file.parts:
                continue
                
            errors = check_doc_meta(md_file)
            all_errors.extend(errors)
            checked_count += 1
    
    # Report results
    if all_errors:
        print("Doc Meta check failed:\n")
        for error in all_errors:
            print(f" - {error}")
        print(f"\nChecked {checked_count} files, found {len(all_errors)} issues")
        print("\nTo fix: Add Doc Meta block to documents. See docs/NEW_DOC_TEMPLATE.md")
        sys.exit(1)
    else:
        print(f"Doc Meta check passed ✅ ({checked_count} files checked)")

if __name__ == "__main__":
    main()