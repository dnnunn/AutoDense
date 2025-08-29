#!/usr/bin/env python3
"""
Config Drift Check Script for Pre-commit Hooks

Detects when hardcoded parameters are added back to Java source files that should
be using YAML configuration instead. Part of the guardrails system to prevent
regression after systematic hardcoded parameter removal.

Usage:
    python scripts/check_config_drift.py [--verbose] [file1.java file2.java ...]
    
Exit codes:
    0: No config drift detected
    1: Config drift detected - hardcoded parameters found
    2: Script error or invalid usage
"""

import re
import sys
import argparse
from pathlib import Path
from typing import List, Dict, Set, Tuple, Optional
from dataclasses import dataclass

@dataclass
class HardcodedPattern:
    """Pattern definition for detecting hardcoded parameters."""
    name: str
    pattern: str
    description: str
    context_hint: str = ""

# Patterns for detecting hardcoded parameters that should be configurable
HARDCODED_PATTERNS = [
    # Background removal parameters
    HardcodedPattern(
        name="background_removal_radius",
        pattern=r'(?:rollingBall|BackgroundSubtracter|backgroundRemoval).*?(\d+(?:\.\d+)?)',
        description="Hardcoded background removal radius",
        context_hint="Should use config.pre.background_removal_radius"
    ),
    
    # Gaussian blur parameters
    HardcodedPattern(
        name="gaussian_sigma",
        pattern=r'(?:GaussianBlur|gaussianBlur|sigma)\s*[=:]\s*(\d+(?:\.\d+)?)',
        description="Hardcoded Gaussian blur sigma",
        context_hint="Should use config.pre.gaussian_sigma"
    ),
    
    # Threshold parameters
    HardcodedPattern(
        name="threshold_radius", 
        pattern=r'(?:LocalThreshold|threshold).*?radius.*?(\d+)',
        description="Hardcoded threshold radius",
        context_hint="Should use config.detect.threshold_radius"
    ),
    
    # Contrast enhancement parameters
    HardcodedPattern(
        name="contrast_saturated",
        pattern=r'(?:enhance|contrast|saturated)\s*[=:]\s*(\d+\.\d+)',
        description="Hardcoded contrast enhancement value",
        context_hint="Should use config.pre.contrast_saturated"
    ),
    
    # Percentile clipping
    HardcodedPattern(
        name="clip_percentile",
        pattern=r'(?:percentile|clip).*?(\d+\.\d+|\d+)(?=\s*[,\)])',
        description="Hardcoded percentile clipping value", 
        context_hint="Should use config.pre.clip_percentiles"
    ),
    
    # Prominence/sensitivity detection
    HardcodedPattern(
        name="prominence_frac",
        pattern=r'prominence.*?(\d+\.\d+)',
        description="Hardcoded prominence fraction",
        context_hint="Should use config.detect.prominence_frac"
    ),
    
    # Colony detection parameters
    HardcodedPattern(
        name="min_colony_size",
        pattern=r'(?:min.*?size|minSize).*?(\d+(?:\.\d+)?)',
        description="Hardcoded minimum colony size",
        context_hint="Should use config.detect.min_colony_size"
    ),
    
    # Peak detection distances
    HardcodedPattern(
        name="peak_distance",
        pattern=r'(?:peak.*?distance|distance.*?px).*?(\d+)',
        description="Hardcoded peak detection distance",
        context_hint="Should use config.detect.min_peak_distance_px"
    ),
    
    # Magic number thresholds (common problematic values)
    HardcodedPattern(
        name="magic_threshold",
        pattern=r'(?:>|<|>=|<=|==)\s*(\b(?:0\.1|0\.3|0\.5|2\.5|50|100|255)\b)',
        description="Hardcoded magic number threshold",
        context_hint="Consider making this configurable"
    )
]

# File extensions to check
JAVA_EXTENSIONS = {'.java'}

# Directories to exclude from checks
EXCLUDED_DIRS = {'target', '.git', 'node_modules', '.idea'}

# Files to exclude (test files, generated code, etc.)
EXCLUDED_FILES = {
    'test', 'Test', 'Mock', 'Stub', 'Generated', 
    'Constants.java', 'Config.java'
}

def should_check_file(file_path: Path) -> bool:
    """Determine if file should be checked for config drift."""
    
    # Check file extension
    if file_path.suffix not in JAVA_EXTENSIONS:
        return False
    
    # Check if in excluded directory
    for part in file_path.parts:
        if part in EXCLUDED_DIRS:
            return False
            
    # Check if excluded file pattern
    for excluded in EXCLUDED_FILES:
        if excluded in file_path.name:
            return False
            
    return True

def extract_context(content: str, line_num: int, context_lines: int = 2) -> str:
    """Extract context around a line for better error reporting."""
    lines = content.split('\n')
    start = max(0, line_num - context_lines - 1)
    end = min(len(lines), line_num + context_lines)
    
    context = []
    for i in range(start, end):
        marker = ">>> " if i == line_num - 1 else "    "
        context.append(f"{marker}{i+1:4d}: {lines[i]}")
    
    return '\n'.join(context)

def check_file_for_drift(file_path: Path, patterns: List[HardcodedPattern]) -> List[Tuple[str, int, str, str]]:
    """
    Check a single file for config drift.
    
    Returns:
        List of (pattern_name, line_number, matched_value, description) tuples
    """
    issues = []
    
    try:
        content = file_path.read_text(encoding='utf-8')
        lines = content.split('\n')
        
        for pattern_def in patterns:
            pattern = re.compile(pattern_def.pattern, re.IGNORECASE)
            
            for line_num, line in enumerate(lines, 1):
                # Skip comments and string literals to reduce false positives
                if line.strip().startswith('//') or line.strip().startswith('*'):
                    continue
                    
                matches = pattern.finditer(line)
                for match in matches:
                    # Extract the numeric value
                    matched_value = match.group(1) if match.groups() else match.group(0)
                    
                    # Additional heuristics to reduce false positives
                    if is_likely_hardcoded(line, matched_value):
                        issues.append((
                            pattern_def.name,
                            line_num, 
                            matched_value,
                            pattern_def.description,
                            pattern_def.context_hint,
                            extract_context(content, line_num)
                        ))
                    
    except Exception as e:
        print(f"Error reading {file_path}: {e}", file=sys.stderr)
        
    return issues

def is_likely_hardcoded(line: str, value: str) -> bool:
    """
    Apply heuristics to determine if a numeric value is likely hardcoded.
    Reduces false positives from legitimate constants.
    """
    line_lower = line.lower()
    
    # Skip if it's clearly a constant declaration
    if any(keyword in line_lower for keyword in ['final', 'static final', 'const']):
        return False
        
    # Skip if it's array indexing or size declaration
    if '[' in line and ']' in line:
        return False
        
    # Skip if it's in a mathematical expression with variables
    if any(op in line for op in ['+', '-', '*', '/', '%']) and any(c.isalpha() for c in line):
        return False
        
    # Skip very small integers that are likely legitimate (0, 1, 2)
    try:
        if float(value) <= 2 and '.' not in value:
            return False
    except ValueError:
        pass
        
    return True

def find_java_files(paths: List[Path]) -> Set[Path]:
    """Find all Java files to check from given paths."""
    java_files = set()
    
    for path in paths:
        if path.is_file():
            if should_check_file(path):
                java_files.add(path)
        elif path.is_dir():
            for java_file in path.rglob('*.java'):
                if should_check_file(java_file):
                    java_files.add(java_file)
    
    return java_files

def print_drift_report(issues_by_file: Dict[Path, List], verbose: bool = False):
    """Print formatted report of config drift issues."""
    
    total_issues = sum(len(issues) for issues in issues_by_file.values())
    total_files = len([f for f, issues in issues_by_file.items() if issues])
    
    if total_issues == 0:
        print("✅ No config drift detected - all parameters are properly configured!")
        return
    
    print(f"❌ Config drift detected: {total_issues} hardcoded parameters found in {total_files} files")
    print()
    
    for file_path, issues in issues_by_file.items():
        if not issues:
            continue
            
        print(f"📁 {file_path}")
        print("=" * (len(str(file_path)) + 2))
        
        for pattern_name, line_num, value, description, context_hint, context in issues:
            print(f"  Line {line_num}: {description}")
            print(f"    Value: {value}")
            print(f"    Hint: {context_hint}")
            
            if verbose:
                print(f"    Context:")
                for context_line in context.split('\n'):
                    print(f"      {context_line}")
            
            print()
        
        print()

def main():
    parser = argparse.ArgumentParser(
        description="Check for config drift - detect hardcoded parameters that should be configurable"
    )
    parser.add_argument(
        'paths', 
        nargs='*', 
        default=['.'],
        help="Files or directories to check (default: current directory)"
    )
    parser.add_argument(
        '--verbose', '-v',
        action='store_true',
        help="Show code context around issues"
    )
    parser.add_argument(
        '--pattern', '-p',
        action='append',
        help="Additional regex patterns to check"
    )
    
    args = parser.parse_args()
    
    # Convert paths to Path objects
    check_paths = [Path(p) for p in args.paths]
    
    # Validate paths exist
    for path in check_paths:
        if not path.exists():
            print(f"Error: Path does not exist: {path}", file=sys.stderr)
            return 2
    
    # Find Java files to check
    java_files = find_java_files(check_paths)
    
    if not java_files:
        print("No Java files found to check")
        return 0
    
    print(f"Checking {len(java_files)} Java files for config drift...")
    
    # Add any custom patterns from command line
    patterns = HARDCODED_PATTERNS.copy()
    if args.pattern:
        for i, pattern_str in enumerate(args.pattern):
            patterns.append(HardcodedPattern(
                name=f"custom_{i}",
                pattern=pattern_str,
                description="Custom pattern match"
            ))
    
    # Check each file
    issues_by_file = {}
    for file_path in sorted(java_files):
        issues = check_file_for_drift(file_path, patterns)
        issues_by_file[file_path] = issues
    
    # Generate report
    print_drift_report(issues_by_file, args.verbose)
    
    # Return appropriate exit code
    total_issues = sum(len(issues) for issues in issues_by_file.values())
    return 1 if total_issues > 0 else 0

if __name__ == "__main__":
    sys.exit(main())