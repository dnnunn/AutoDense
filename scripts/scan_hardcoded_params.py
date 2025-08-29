#!/usr/bin/env python3
"""
Comprehensive Hardcoded Parameter Scanner

Performs deep analysis to find ALL hardcoded parameters in Java source files
that could potentially be made configurable. Used for systematic audits and
ensuring complete parameter externalization.

Usage:
    python scripts/scan_hardcoded_params.py [--output report.json] [--format json|text] [path ...]
    
Features:
    - Comprehensive pattern matching for numeric literals in parameter contexts
    - Context analysis to distinguish constants from parameters
    - Categorization by parameter type (preprocessing, detection, analysis)
    - JSON output for integration with other tools
    - Severity scoring based on likelihood of being configurable
"""

import re
import sys
import json
import argparse
from pathlib import Path
from typing import List, Dict, Set, Tuple, Optional, Any
from dataclasses import dataclass, asdict
from enum import Enum
import ast

class ParameterCategory(Enum):
    """Categories of parameters for systematic organization."""
    PREPROCESSING = "preprocessing"
    DETECTION = "detection" 
    ANALYSIS = "analysis"
    VISUALIZATION = "visualization"
    PERFORMANCE = "performance"
    UNKNOWN = "unknown"

class Severity(Enum):
    """Severity levels for hardcoded parameter findings."""
    CRITICAL = "critical"      # Definitely should be configurable
    HIGH = "high"             # Likely should be configurable  
    MEDIUM = "medium"         # Possibly should be configurable
    LOW = "low"              # Probably OK as hardcoded
    INFO = "info"            # Informational only

@dataclass
class HardcodedFinding:
    """A finding of a potentially hardcoded parameter."""
    file_path: str
    line_number: int
    parameter_name: str
    value: str
    category: ParameterCategory
    severity: Severity
    description: str
    context: str
    suggested_config_path: str = ""
    confidence: float = 0.5

# Comprehensive patterns for finding hardcoded parameters
PARAMETER_PATTERNS = [
    # Image preprocessing parameters
    {
        'name': 'background_removal',
        'patterns': [
            r'(?:rollingBall|BackgroundSubtracter|subtractBackground).*?[(\s,](\d+(?:\.\d+)?)',
            r'(?:background.*?radius|radius.*?background).*?[=:\s](\d+(?:\.\d+)?)',
        ],
        'category': ParameterCategory.PREPROCESSING,
        'severity': Severity.CRITICAL,
        'config_path': 'pre.background_removal_radius'
    },
    
    # Gaussian filtering
    {
        'name': 'gaussian_blur',
        'patterns': [
            r'(?:GaussianBlur|gaussianBlur|gaussian).*?[(\s,](\d+(?:\.\d+)?)',
            r'(?:sigma|blur.*?radius).*?[=:\s](\d+(?:\.\d+)?)',
            r'\.blur\s*\([^)]*?(\d+(?:\.\d+)?)',
        ],
        'category': ParameterCategory.PREPROCESSING,
        'severity': Severity.CRITICAL,
        'config_path': 'pre.gaussian_sigma'
    },
    
    # Thresholding parameters
    {
        'name': 'threshold_params',
        'patterns': [
            r'(?:LocalThreshold|autoThreshold|threshold).*?[(\s,](\d+(?:\.\d+)?)',
            r'(?:threshold.*?radius|radius.*?threshold).*?[=:\s](\d+)',
            r'(?:prominence|threshold).*?[=:\s](\d+\.\d+)',
        ],
        'category': ParameterCategory.DETECTION,
        'severity': Severity.HIGH,
        'config_path': 'detect.threshold_radius'
    },
    
    # Contrast and intensity adjustments
    {
        'name': 'contrast_enhancement',
        'patterns': [
            r'(?:enhance|contrast|saturated).*?[=:\s](\d+\.\d+)',
            r'(?:ContrastEnhancer|normalize).*?[(\s,](\d+\.\d+)',
            r'(?:clipper|percentile).*?[(\s,](\d+(?:\.\d+)?)',
        ],
        'category': ParameterCategory.PREPROCESSING,
        'severity': Severity.HIGH,
        'config_path': 'pre.contrast_saturated'
    },
    
    # Morphological operations
    {
        'name': 'morphological_ops',
        'patterns': [
            r'(?:erode|dilate|open|close).*?[(\s,](\d+)',
            r'(?:structuring.*?element|kernel.*?size).*?[=:\s](\d+)',
            r'(?:morphology|binary).*?[(\s,](\d+)',
        ],
        'category': ParameterCategory.PREPROCESSING,
        'severity': Severity.MEDIUM,
        'config_path': 'pre.morphology_kernel_size'
    },
    
    # Detection sensitivity and distances
    {
        'name': 'detection_sensitivity',
        'patterns': [
            r'(?:prominence|sensitivity).*?[=:\s](\d+\.\d+)',
            r'(?:peak.*?distance|distance.*?px).*?[=:\s](\d+)',
            r'(?:min.*?distance|minDistance).*?[=:\s](\d+)',
        ],
        'category': ParameterCategory.DETECTION,
        'severity': Severity.CRITICAL,
        'config_path': 'detect.prominence_frac'
    },
    
    # Size constraints (colony, band, etc.)
    {
        'name': 'size_constraints',
        'patterns': [
            r'(?:min.*?size|minSize).*?[=:\s](\d+(?:\.\d+)?)',
            r'(?:max.*?size|maxSize).*?[=:\s](\d+(?:\.\d+)?)',
            r'(?:min.*?area|minArea).*?[=:\s](\d+(?:\.\d+)?)',
        ],
        'category': ParameterCategory.DETECTION,
        'severity': Severity.HIGH,
        'config_path': 'detect.min_size'
    },
    
    # Color space and channel parameters
    {
        'name': 'color_processing',
        'patterns': [
            r'(?:hue|saturation|brightness).*?[=:\s](\d+(?:\.\d+)?)',
            r'(?:lab|hsv|rgb).*?threshold.*?[=:\s](\d+(?:\.\d+)?)',
            r'(?:blue.*?index|color.*?distance).*?[=:\s](\d+\.\d+)',
        ],
        'category': ParameterCategory.ANALYSIS,
        'severity': Severity.MEDIUM,
        'config_path': 'analysis.color_threshold'
    },
    
    # Calibration and measurement
    {
        'name': 'calibration_params',
        'patterns': [
            r'(?:pixel.*?size|pixels.*?per.*?unit).*?[=:\s](\d+\.\d+)',
            r'(?:calibration|scale).*?factor.*?[=:\s](\d+(?:\.\d+)?)',
            r'(?:units.*?per.*?pixel|mm.*?per.*?pixel).*?[=:\s](\d+\.\d+)',
        ],
        'category': ParameterCategory.ANALYSIS,
        'severity': Severity.MEDIUM,
        'config_path': 'analysis.pixel_size'
    },
    
    # Performance and memory parameters
    {
        'name': 'performance_params',
        'patterns': [
            r'(?:buffer.*?size|cache.*?size).*?[=:\s](\d+)',
            r'(?:thread.*?count|pool.*?size).*?[=:\s](\d+)',
            r'(?:timeout|delay).*?[=:\s](\d+)',
        ],
        'category': ParameterCategory.PERFORMANCE,
        'severity': Severity.LOW,
        'config_path': 'performance.buffer_size'
    }
]

# Context keywords that suggest a parameter might be configurable
CONFIGURABLE_CONTEXTS = {
    'preprocessing': ['preprocess', 'filter', 'enhance', 'normalize', 'clip'],
    'detection': ['detect', 'find', 'locate', 'threshold', 'peak', 'prominence'],
    'analysis': ['analyze', 'measure', 'calculate', 'quantify', 'score'],
    'algorithm': ['algorithm', 'method', 'strategy', 'approach'],
    'parameter': ['param', 'setting', 'config', 'option', 'value']
}

# Context keywords that suggest a value is likely a constant
CONSTANT_CONTEXTS = {
    'array_size': ['array', 'buffer', 'size', 'length', 'count'],
    'loop_control': ['for', 'while', 'iterate', 'index', 'step'],
    'constants': ['final', 'static', 'const', 'immutable'],
    'coordinates': ['x', 'y', 'width', 'height', 'position'],
    'math': ['pi', 'e', 'sqrt', 'pow', 'abs', 'sin', 'cos']
}

def calculate_severity(context: str, value: str, method_name: str = "") -> Severity:
    """Calculate severity based on context analysis."""
    
    context_lower = context.lower()
    method_lower = method_name.lower()
    
    # Critical: Definitely algorithmic parameters
    if any(keyword in context_lower for keyword in ['prominence', 'threshold', 'sigma', 'radius']):
        return Severity.CRITICAL
        
    # High: Likely configurable parameters
    if any(keyword in method_lower for keyword in ['detect', 'analyze', 'process']):
        return Severity.HIGH
        
    # Medium: Possibly configurable
    if any(keyword in context_lower for keyword in ['size', 'distance', 'factor']):
        return Severity.MEDIUM
        
    # Low: Likely constants
    if any(keyword in context_lower for keyword in ['final', 'static', 'const']):
        return Severity.LOW
        
    # Try to evaluate numeric value
    try:
        num_val = float(value)
        if num_val <= 2:  # Small integers often constants
            return Severity.LOW
        elif num_val > 100:  # Large values often parameters
            return Severity.MEDIUM
    except ValueError:
        pass
        
    return Severity.INFO

def extract_method_context(lines: List[str], line_idx: int) -> str:
    """Extract the method name containing this line."""
    
    # Look backward for method declaration
    for i in range(line_idx, max(-1, line_idx - 20), -1):
        line = lines[i].strip()
        # Simple regex for Java method declarations
        method_match = re.search(r'(?:public|private|protected|static).*?\s(\w+)\s*\(', line)
        if method_match:
            return method_match.group(1)
    
    return ""

def analyze_file(file_path: Path) -> List[HardcodedFinding]:
    """Analyze a Java file for hardcoded parameters."""
    
    findings = []
    
    try:
        content = file_path.read_text(encoding='utf-8')
        lines = content.split('\n')
        
        for pattern_group in PARAMETER_PATTERNS:
            for pattern_str in pattern_group['patterns']:
                pattern = re.compile(pattern_str, re.IGNORECASE)
                
                for line_idx, line in enumerate(lines):
                    # Skip comments and string literals
                    if line.strip().startswith('//') or line.strip().startswith('*'):
                        continue
                        
                    matches = pattern.finditer(line)
                    for match in matches:
                        value = match.group(1)
                        
                        # Get context
                        start_line = max(0, line_idx - 2)
                        end_line = min(len(lines), line_idx + 3)
                        context = '\n'.join(f"{i+1:4d}: {lines[i]}" for i in range(start_line, end_line))
                        
                        # Get method context
                        method_name = extract_method_context(lines, line_idx)
                        
                        # Calculate severity
                        severity = calculate_severity(line, value, method_name)
                        
                        # Calculate confidence based on context
                        confidence = calculate_confidence(line, value, method_name)
                        
                        finding = HardcodedFinding(
                            file_path=str(file_path),
                            line_number=line_idx + 1,
                            parameter_name=pattern_group['name'],
                            value=value,
                            category=pattern_group['category'],
                            severity=severity,
                            description=f"Hardcoded {pattern_group['name']} parameter",
                            context=context,
                            suggested_config_path=pattern_group.get('config_path', ''),
                            confidence=confidence
                        )
                        
                        findings.append(finding)
                        
    except Exception as e:
        print(f"Error analyzing {file_path}: {e}", file=sys.stderr)
    
    return findings

def calculate_confidence(line: str, value: str, method_name: str) -> float:
    """Calculate confidence that this is a hardcoded parameter (0.0 to 1.0)."""
    
    confidence = 0.5  # Base confidence
    
    line_lower = line.lower()
    method_lower = method_name.lower()
    
    # Increase confidence for parameter-like contexts
    if any(keyword in method_lower for keyword in ['detect', 'process', 'analyze', 'filter']):
        confidence += 0.2
        
    if any(keyword in line_lower for keyword in ['threshold', 'sigma', 'radius', 'prominence']):
        confidence += 0.3
        
    # Decrease confidence for constant-like contexts
    if any(keyword in line_lower for keyword in ['final', 'static', 'const']):
        confidence -= 0.4
        
    if 'new int[' in line or 'new double[' in line:
        confidence -= 0.3
        
    # Value-based adjustments
    try:
        num_val = float(value)
        if num_val <= 2 and '.' not in value:  # Small integers
            confidence -= 0.2
        elif 10 <= num_val <= 100:  # Common parameter range
            confidence += 0.1
    except ValueError:
        pass
    
    return max(0.0, min(1.0, confidence))

def generate_text_report(findings_by_file: Dict[Path, List[HardcodedFinding]]) -> str:
    """Generate human-readable text report."""
    
    report_lines = []
    
    # Summary
    total_findings = sum(len(findings) for findings in findings_by_file.values())
    files_with_findings = len([f for f, findings in findings_by_file.items() if findings])
    
    report_lines.append("=" * 80)
    report_lines.append("HARDCODED PARAMETER SCAN REPORT")
    report_lines.append("=" * 80)
    report_lines.append(f"Total findings: {total_findings}")
    report_lines.append(f"Files affected: {files_with_findings}")
    report_lines.append("")
    
    # Group by severity
    by_severity = {}
    for findings in findings_by_file.values():
        for finding in findings:
            severity = finding.severity.value
            if severity not in by_severity:
                by_severity[severity] = []
            by_severity[severity].append(finding)
    
    report_lines.append("SUMMARY BY SEVERITY:")
    for severity in ['critical', 'high', 'medium', 'low', 'info']:
        count = len(by_severity.get(severity, []))
        report_lines.append(f"  {severity.upper()}: {count}")
    
    report_lines.append("")
    report_lines.append("=" * 80)
    
    # Detailed findings
    for file_path, findings in findings_by_file.items():
        if not findings:
            continue
            
        report_lines.append(f"\n📁 {file_path}")
        report_lines.append("-" * len(str(file_path)))
        
        # Sort by severity then line number
        severity_order = {'critical': 0, 'high': 1, 'medium': 2, 'low': 3, 'info': 4}
        findings.sort(key=lambda f: (severity_order.get(f.severity.value, 5), f.line_number))
        
        for finding in findings:
            severity_icon = {
                'critical': '🔴',
                'high': '🟠', 
                'medium': '🟡',
                'low': '🔵',
                'info': '⚪'
            }.get(finding.severity.value, '❓')
            
            report_lines.append(f"\n  {severity_icon} Line {finding.line_number}: {finding.parameter_name}")
            report_lines.append(f"     Value: {finding.value}")
            report_lines.append(f"     Severity: {finding.severity.value.upper()}")
            report_lines.append(f"     Confidence: {finding.confidence:.2f}")
            if finding.suggested_config_path:
                report_lines.append(f"     Suggested config: {finding.suggested_config_path}")
            report_lines.append("")
    
    return '\n'.join(report_lines)

def generate_json_report(findings_by_file: Dict[Path, List[HardcodedFinding]]) -> Dict[str, Any]:
    """Generate machine-readable JSON report."""
    
    all_findings = []
    for findings in findings_by_file.values():
        all_findings.extend(findings)
    
    # Summary statistics
    total_findings = len(all_findings)
    by_severity = {}
    by_category = {}
    
    for finding in all_findings:
        severity = finding.severity.value
        category = finding.category.value
        
        by_severity[severity] = by_severity.get(severity, 0) + 1
        by_category[category] = by_category.get(category, 0) + 1
    
    return {
        'summary': {
            'total_findings': total_findings,
            'files_scanned': len(findings_by_file),
            'files_with_findings': len([f for f, findings in findings_by_file.items() if findings]),
            'by_severity': by_severity,
            'by_category': by_category
        },
        'findings': [asdict(finding) for finding in all_findings]
    }

def main():
    parser = argparse.ArgumentParser(description="Scan for hardcoded parameters in Java code")
    parser.add_argument('paths', nargs='*', default=['.'], help="Paths to scan")
    parser.add_argument('--output', '-o', help="Output file path")
    parser.add_argument('--format', '-f', choices=['text', 'json'], default='text', 
                       help="Output format")
    parser.add_argument('--min-confidence', type=float, default=0.3,
                       help="Minimum confidence threshold (0.0-1.0)")
    parser.add_argument('--severity', choices=['critical', 'high', 'medium', 'low', 'info'],
                       help="Only show findings of this severity or higher")
    
    args = parser.parse_args()
    
    # Find Java files
    java_files = set()
    for path_str in args.paths:
        path = Path(path_str)
        if path.is_file() and path.suffix == '.java':
            java_files.add(path)
        elif path.is_dir():
            for java_file in path.rglob('*.java'):
                # Skip test files and generated code
                if not any(skip in java_file.name for skip in ['Test', 'test', 'Mock', 'Generated']):
                    java_files.add(java_file)
    
    if not java_files:
        print("No Java files found to scan")
        return 0
    
    print(f"Scanning {len(java_files)} Java files for hardcoded parameters...")
    
    # Analyze files
    findings_by_file = {}
    for java_file in sorted(java_files):
        findings = analyze_file(java_file)
        
        # Apply filters
        filtered_findings = []
        for finding in findings:
            if finding.confidence >= args.min_confidence:
                if args.severity:
                    severity_order = {'info': 0, 'low': 1, 'medium': 2, 'high': 3, 'critical': 4}
                    min_level = severity_order[args.severity]
                    finding_level = severity_order[finding.severity.value]
                    if finding_level >= min_level:
                        filtered_findings.append(finding)
                else:
                    filtered_findings.append(finding)
        
        findings_by_file[java_file] = filtered_findings
    
    # Generate report
    if args.format == 'json':
        report_data = generate_json_report(findings_by_file)
        report_content = json.dumps(report_data, indent=2, default=str)
    else:
        report_content = generate_text_report(findings_by_file)
    
    # Output report
    if args.output:
        Path(args.output).write_text(report_content, encoding='utf-8')
        print(f"Report written to {args.output}")
    else:
        print(report_content)
    
    # Return exit code based on critical findings
    critical_findings = sum(
        1 for findings in findings_by_file.values() 
        for finding in findings 
        if finding.severity == Severity.CRITICAL
    )
    
    return 1 if critical_findings > 0 else 0

if __name__ == "__main__":
    sys.exit(main())