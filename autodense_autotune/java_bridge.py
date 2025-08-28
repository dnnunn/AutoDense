"""
Python-Java bridge for calling AutoDense ImageJ analysis from the autotune system.

This module provides the interface between the Python optimization system and the 
Java ImageJ-based analysis tools. It handles:
- Java classpath construction
- Process execution and monitoring  
- Result parsing and validation
- Error handling and logging

Usage:
    python -m autodense_autotune.java_bridge <task> --input <image> --config <config> --outdir <output>
"""

import argparse
import json
import subprocess
import sys
import os
from pathlib import Path
import logging
import shutil
import time
from typing import Dict, List, Optional, Tuple

logger = logging.getLogger(__name__)

class JavaBridgeError(Exception):
    """Exception raised when Java analysis fails"""
    pass

def find_java_executable() -> str:
    """Find Java executable, preferring JAVA_HOME if set"""
    java_home = os.environ.get('JAVA_HOME')
    if java_home:
        java_exe = Path(java_home) / 'bin' / 'java'
        if java_exe.exists():
            logger.debug(f"Using Java from JAVA_HOME: {java_exe}")
            return str(java_exe)
    
    # Fall back to system java
    java_path = shutil.which('java')
    if java_path:
        logger.debug(f"Using system Java: {java_path}")
        return java_path
    
    raise JavaBridgeError("Java executable not found. Please install Java or set JAVA_HOME.")

def ensure_maven_build() -> bool:
    """Ensure Maven project is built, building if necessary"""
    project_root = Path(__file__).parent.parent
    pom_dir = project_root / os.environ.get('POM_DIR', 'autodense/plugin')
    
    if not (pom_dir / 'pom.xml').exists():
        raise JavaBridgeError(f"Maven POM not found at {pom_dir}/pom.xml")
    
    classes_dir = pom_dir / 'target' / 'classes'
    
    # Check if build is needed
    if classes_dir.exists() and any(classes_dir.rglob('*.class')):
        logger.debug("Maven build appears current")
        return True
    
    logger.info("Running Maven build...")
    mvn_exe = shutil.which('mvn')
    if not mvn_exe:
        raise JavaBridgeError("Maven executable not found. Please install Maven.")
    
    try:
        result = subprocess.run(
            [mvn_exe, 'compile', 'dependency:build-classpath', '-Dmdep.outputFile=target/classpath.txt'],
            cwd=pom_dir,
            capture_output=True,
            text=True,
            timeout=300
        )
        
        if result.returncode != 0:
            raise JavaBridgeError(f"Maven build failed: {result.stderr}")
        
        logger.info("Maven build completed successfully")
        return True
        
    except subprocess.TimeoutExpired:
        raise JavaBridgeError("Maven build timed out")

def discover_fiji_installation() -> Optional[str]:
    """Discover Fiji/ImageJ installation using environment or auto-discovery"""
    # Check environment variables first
    for env_var in ['FIJI_DIR', 'IMAGEJ_DIR']:
        fiji_dir = os.environ.get(env_var)
        if fiji_dir and Path(fiji_dir).exists():
            logger.debug(f"Using Fiji from {env_var}: {fiji_dir}")
            return fiji_dir
    
    # Try to use the discover_imagej script
    discovery_script = Path(__file__).parent.parent / 'scripts' / 'discover_imagej.py'
    if discovery_script.exists():
        try:
            result = subprocess.run(
                [sys.executable, str(discovery_script), '--json'],
                capture_output=True,
                text=True,
                timeout=30
            )
            
            if result.returncode == 0:
                discovery_data = json.loads(result.stdout)
                primary = discovery_data.get('primary_installation')
                if primary:
                    logger.info(f"Auto-discovered Fiji installation: {primary['path']}")
                    return primary['path']
        except (subprocess.TimeoutExpired, json.JSONDecodeError, KeyError) as e:
            logger.warning(f"Auto-discovery failed: {e}")
    
    return None

def build_classpath() -> str:
    """Build comprehensive classpath for AutoDense and dependencies"""
    # Get the project root directory  
    project_root = Path(__file__).parent.parent
    pom_dir = project_root / os.environ.get('POM_DIR', 'autodense/plugin')
    
    # Ensure Maven build is current
    ensure_maven_build()
    
    classpath_parts = []
    
    # Add compiled classes
    classes_dir = pom_dir / 'target' / 'classes'
    if classes_dir.exists():
        classpath_parts.append(str(classes_dir))
    
    # Add Maven dependencies from generated classpath
    maven_classpath_file = pom_dir / 'target' / 'classpath.txt'
    if maven_classpath_file.exists():
        maven_classpath = maven_classpath_file.read_text().strip()
        if maven_classpath:
            # Split by platform-appropriate separator
            separator = ';' if os.name == 'nt' else ':'
            classpath_parts.extend(maven_classpath.split(separator))
    else:
        # Fallback to dependency directory
        dep_dir = pom_dir / 'target' / 'dependency'
        if dep_dir.exists():
            classpath_parts.append(str(dep_dir / '*'))
    
    # Try to add Fiji/ImageJ JARs
    fiji_dir = discover_fiji_installation()
    if fiji_dir:
        fiji_path = Path(fiji_dir)
        
        # Look for JARs in Fiji installation
        potential_jar_dirs = [
            fiji_path / 'jars',
            fiji_path / 'Contents' / 'java' / 'jars',
            fiji_path / 'Contents' / 'Resources' / 'java',
            fiji_path / 'lib'
        ]
        
        for jar_dir in potential_jar_dirs:
            if jar_dir.exists() and any(jar_dir.glob('*.jar')):
                classpath_parts.append(str(jar_dir / '*'))
                logger.debug(f"Added Fiji JARs from: {jar_dir}")
                break
    
    # Additional lib directory (project-specific)
    lib_dir = project_root / 'lib'
    if lib_dir.exists():
        classpath_parts.append(str(lib_dir / '*'))
    
    # Platform-appropriate classpath separator
    separator = ';' if os.name == 'nt' else ':'
    final_classpath = separator.join(classpath_parts)
    
    logger.debug(f"Built classpath with {len(classpath_parts)} components")
    return final_classpath

def run_java_analysis(task: str, input_path: str, config_path: str, output_dir: str, 
                      executable_type: str = 'main') -> dict:
    """
    Execute Java AutoDense analysis via CLI
    
    Args:
        task: Analysis task type (sds_page, colony_count, etbr_agarose)
        input_path: Path to input image
        config_path: Path to configuration file
        output_dir: Output directory for results
        
    Returns:
        Dict containing analysis results
        
    Raises:
        JavaBridgeError: If analysis fails
    """
    
    # Prepare execution environment
    java_exe = find_java_executable()
    classpath = build_classpath()
    main_class = os.environ.get('AUTODENSE_MAIN_CLASS', 
                              'com.betterdairy.autodense.cli.AutotuneAnalysisCLI')
    
    # Convert paths to absolute paths
    input_path = str(Path(input_path).resolve())
    config_path = str(Path(config_path).resolve())
    output_dir = str(Path(output_dir).resolve())
    
    # Ensure output directory exists
    Path(output_dir).mkdir(parents=True, exist_ok=True)
    
    # Get memory and JVM settings from environment
    max_heap = os.environ.get('JAVA_MAX_HEAP', '4g')
    java_opts = os.environ.get('JAVA_OPTS', '').split()
    
    # Construct command based on execution type
    cmd = [
        java_exe,
        '-cp', classpath,
        '-Djava.awt.headless=true',
        f'-Xmx{max_heap}',
        *java_opts,
        main_class,
        task,
        input_path,
        config_path,
        output_dir
    ]
    
    # Get timeout from environment
    timeout = int(os.environ.get('ANALYSIS_TIMEOUT', '300'))
    
    logger.info(f"Executing {executable_type} analysis with timeout {timeout}s")
    logger.debug(f"Command: {' '.join(cmd[:5])}... ({len(cmd)} args total)")
    
    # Capture start time for performance monitoring
    start_time = time.time()
    
    try:
        # Execute Java process
        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=300,  # 5 minute timeout
            check=False  # Don't raise exception on non-zero exit
        )
        
        if result.returncode != 0:
            error_msg = f"Java analysis failed with exit code {result.returncode}"
            if result.stderr:
                error_msg += f"\nStderr: {result.stderr}"
            if result.stdout:
                error_msg += f"\nStdout: {result.stdout}"
            raise JavaBridgeError(error_msg)
        
        # Read the run_report.json file
        report_path = Path(output_dir) / "run_report.json"
        if not report_path.exists():
            raise JavaBridgeError(f"Expected output file not found: {report_path}")
        
        with open(report_path, 'r', encoding='utf-8') as f:
            report_data = json.load(f)
        
        logger.info(f"Java analysis completed successfully: {report_path}")
        return report_data
        
    except subprocess.TimeoutExpired:
        raise JavaBridgeError("Java analysis timed out after 5 minutes")
    except json.JSONDecodeError as e:
        raise JavaBridgeError(f"Failed to parse analysis results: {e}")
    except Exception as e:
        raise JavaBridgeError(f"Unexpected error during Java analysis: {e}")

# Health check function
def check_environment() -> Dict[str, any]:
    """Check if all required components are available"""
    checks = {}
    
    try:
        java_exe = find_java_executable()
        checks['java'] = {'status': 'ok', 'path': java_exe}
    except JavaBridgeError as e:
        checks['java'] = {'status': 'error', 'error': str(e)}
    
    try:
        ensure_maven_build()
        checks['maven'] = {'status': 'ok'}
    except JavaBridgeError as e:
        checks['maven'] = {'status': 'error', 'error': str(e)}
    
    fiji_dir = discover_fiji_installation()
    if fiji_dir:
        checks['fiji'] = {'status': 'ok', 'path': fiji_dir}
    else:
        checks['fiji'] = {'status': 'warning', 'message': 'Fiji not found - some features may be limited'}
    
    try:
        classpath = build_classpath()
        cp_parts = classpath.split(';' if os.name == 'nt' else ':')
        checks['classpath'] = {'status': 'ok', 'components': len(cp_parts)}
    except Exception as e:
        checks['classpath'] = {'status': 'error', 'error': str(e)}
    
    return checks

def run_sds_page(input_path: str, config_path: str, output_dir: str, 
                 executable_type: str = 'main') -> dict:
    """Run SDS-PAGE analysis via Java bridge"""
    return run_java_analysis("sds_page", input_path, config_path, output_dir, executable_type)

def run_colony_count(input_path: str, config_path: str, output_dir: str,
                    executable_type: str = 'main') -> dict:
    """Run colony counting analysis via Java bridge"""  
    return run_java_analysis("colony_count", input_path, config_path, output_dir, executable_type)

def run_etbr_agarose(input_path: str, config_path: str, output_dir: str,
                    executable_type: str = 'main') -> dict:
    """Run EtBr agarose analysis via Java bridge"""
    return run_java_analysis("etbr_agarose", input_path, config_path, output_dir, executable_type)

def main():
    """Command-line entry point"""
    parser = argparse.ArgumentParser(description="Python-Java bridge for AutoDense analysis")
    parser.add_argument("task", nargs='?', choices=["sds_page", "colony_count", "etbr_agarose"], 
                       help="Analysis task type")
    parser.add_argument("--input", help="Input image path")
    parser.add_argument("--config", help="Configuration file path") 
    parser.add_argument("--outdir", help="Output directory")
    parser.add_argument("--verbose", "-v", action="store_true", help="Enable verbose logging")
    parser.add_argument("--executable-type", choices=["main", "imagej_macro"], 
                       default="main", help="Execution method (default: main)")
    parser.add_argument("--env-check", action="store_true", 
                       help="Check environment and exit")
    
    args = parser.parse_args()
    
    # Configure logging
    log_level = logging.DEBUG if args.verbose else logging.INFO
    logging.basicConfig(
        level=log_level,
        format='%(asctime)s - %(name)s - %(levelname)s - %(message)s'
    )
    
    # Handle environment check
    if args.env_check:
        env_status = check_environment()
        print(json.dumps(env_status, indent=2))
        
        # Exit with appropriate code
        has_errors = any(check.get('status') == 'error' for check in env_status.values())
        sys.exit(1 if has_errors else 0)
    
    # Validate required arguments for analysis tasks
    if not args.task:
        parser.error("task argument is required when not using --env-check")
    if not args.input:
        parser.error("--input is required for analysis tasks")
    if not args.config:
        parser.error("--config is required for analysis tasks") 
    if not args.outdir:
        parser.error("--outdir is required for analysis tasks")
    
    try:
        # Execute analysis
        if args.task == "sds_page":
            result = run_sds_page(args.input, args.config, args.outdir, args.executable_type)
        elif args.task == "colony_count":
            result = run_colony_count(args.input, args.config, args.outdir, args.executable_type)
        elif args.task == "etbr_agarose":
            result = run_etbr_agarose(args.input, args.config, args.outdir, args.executable_type)
        else:
            raise ValueError(f"Unknown task: {args.task}")
        
        # Output result summary to stdout
        print(f"Analysis completed successfully:")
        print(f"Task: {result.get('task')}")
        print(f"Input: {result.get('input_path')}")
        print(f"Metrics: {json.dumps(result.get('metrics', {}), indent=2)}")
        
    except JavaBridgeError as e:
        logger.error(f"Java bridge error: {e}")
        print(f"Error: {e}", file=sys.stderr)
        sys.exit(1)
    except Exception as e:
        logger.error(f"Unexpected error: {e}")
        print(f"Unexpected error: {e}", file=sys.stderr)
        sys.exit(1)

if __name__ == "__main__":
    main()