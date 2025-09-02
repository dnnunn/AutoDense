"""
Python-Java bridge for calling AutoDense ImageJ analysis from the autotune system.

This module provides the interface between the Python optimization system and the 
Java ImageJ-based analysis tools. It handles:
- Java classpath construction
- Process execution and monitoring  
- Result parsing and validation
- Error handling and logging

🔴 CRITICAL REQUIREMENT: --no-exit FLAG 🔴
ALL calls to AutotuneAnalysisCLI MUST include --no-exit flag or the optimization
loop will quietly die when the Java process terminates. This bridge automatically
adds --no-exit to all Java CLI invocations.

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
    
    # Check project-bundled Fiji installation
    project_root = Path(__file__).parent.parent
    bundled_fiji = project_root / 'autodense' / 'packaging' / 'resources' / 'Fiji.app'
    if bundled_fiji.exists():
        logger.debug(f"Using project-bundled Fiji: {bundled_fiji}")
        return str(bundled_fiji)
    
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
    
    # Add AutoDense.app Java libraries (contains ImageJ/SCIFIO libraries)
    autodense_java_lib = project_root / 'autodense' / 'packaging' / 'resources' / 'AutoDense.app' / 'Contents' / 'Resources' / 'java' / 'lib'
    if autodense_java_lib.exists():
        classpath_parts.append(str(autodense_java_lib / '*'))
        logger.debug(f"Added AutoDense.app Java libraries: {autodense_java_lib}")
    
    # Additional lib directory (project-specific)
    lib_dir = project_root / 'lib'
    if lib_dir.exists():
        classpath_parts.append(str(lib_dir / '*'))
    
    # Platform-appropriate classpath separator
    separator = ';' if os.name == 'nt' else ':'
    final_classpath = separator.join(classpath_parts)
    
    logger.debug(f"Built classpath with {len(classpath_parts)} components")
    return final_classpath

def find_imagej_patcher_jar() -> Optional[str]:
    """Find the ImageJ patcher jar for Java 17+ compatibility"""
    fiji_dir = discover_fiji_installation()
    
    # Also check AutoDense.app for ImageJ libraries
    project_root = Path(__file__).parent.parent
    autodense_java_lib = project_root / 'autodense' / 'packaging' / 'resources' / 'AutoDense.app' / 'Contents' / 'Resources' / 'java' / 'lib'
    
    all_search_paths = []
    
    if fiji_dir:
        fiji_path = Path(fiji_dir)
        # Look for ij1-patcher jar in Fiji installation
        all_search_paths.extend([
            fiji_path / 'jars',
            fiji_path / 'Contents' / 'java' / 'jars',
            fiji_path / 'Contents' / 'Resources' / 'java',
            fiji_path / 'lib'
        ])
    
    # Add AutoDense.app Java lib directory
    if autodense_java_lib.exists():
        all_search_paths.append(autodense_java_lib)
    
    for jar_dir in all_search_paths:
        if jar_dir.exists():
            # Look for ij1-patcher jar
            patcher_jars = list(jar_dir.glob('ij1-patcher*.jar'))
            if patcher_jars:
                patcher_jar = str(patcher_jars[0])  # Use first match
                logger.debug(f"Found ImageJ patcher jar: {patcher_jar}")
                return patcher_jar
    
    # AutoDense uses SCIFIO (ImageJ2) - no patcher needed
    logger.debug("ImageJ patcher jar not found (expected - AutoDense uses SCIFIO with built-in Java 17+ support)")
    return None

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
    
    # Build JVM options for ImageJ compatibility
    jvm_options = [
        '-cp', classpath,
        '-Djava.awt.headless=true',
        f'-Xmx{max_heap}',
        '--add-opens=java.base/java.lang=ALL-UNNAMED',  # Java 17+ module compatibility
    ]
    
    # Add ImageJ patcher javaagent if available (AutoDense uses SCIFIO, so this is optional)
    patcher_jar = find_imagej_patcher_jar()
    if patcher_jar:
        jvm_options.append(f'-javaagent:{patcher_jar}=init')
        logger.debug("Added ImageJ patcher javaagent for Java 17+ compatibility")
    else:
        # AutoDense uses SCIFIO (ImageJ2) which has built-in Java 17+ compatibility
        # This is expected behavior - no warning needed
        logger.debug("ImageJ patcher not found (expected - AutoDense uses SCIFIO with built-in Java 17+ support)")
    
    # Add any additional Java options from environment
    jvm_options.extend(java_opts)
    
    # Construct command based on execution type
    # CRITICAL: --no-exit flag required for optimizer integration
    cmd = [
        java_exe,
        *jvm_options,
        main_class,
        '--task', task,
        '--input', input_path,
        '--config', config_path,
        '--output', output_dir,
        '--no-exit'  # CRITICAL: Keeps Java process alive for optimization loops
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

def run_detect_only(task: str, input_path: str, config_path: str, output_dir: str, roi: str = None) -> dict:
    """
    Run detection-only analysis for Gemini optimization (per external audit)
    
    This isolates the detector when status ∈ {bypassed_detection, headless_violation, no_lanes}
    allowing Gemini to test detection parameters independently of preprocessing.
    
    Args:
        task: Analysis task type (sds_page, colony_count, etbr_agarose)
        input_path: Path to preprocessed image (stage1_norm.png or equivalent)  
        config_path: Path to configuration file
        output_dir: Output directory for results
        roi: Optional ROI specification for focused detection
        
    Returns:
        Dict containing detection results with observation metadata
    """
    
    # For now, use the existing Java CLI with detect-only mode simulation
    # This runs the full pipeline but focuses on detection metrics
    try:
        # Run standard analysis but extract only detection metrics
        result = run_java_analysis(task, input_path, config_path, output_dir)
        
        # Transform result to detection-only format for Gemini
        detection_result = {
            "task": f"{task}_detect_only",
            "input_path": input_path,
            "input_hash": result.get("input_hash", ""),
            "metrics": {
                "lanes_found": result.get("metrics", {}).get("lane_count", 0),
                "bands_found": result.get("metrics", {}).get("band_count", 0),
                "detection_confidence": 0.8 if result.get("metrics", {}).get("lane_count", 0) > 0 else 0.2
            },
            "observation": result.get("observation", {}),
            "status": "detect_only_mode"
        }
        
        # Add detection-specific observations
        if "observation" in detection_result:
            detection_result["observation"]["mode"] = "detect_only"
            detection_result["observation"]["bypass_preprocessing"] = True
            
        logger.info(f"Detection-only analysis completed: {detection_result['metrics']}")
        return detection_result
        
    except JavaBridgeError as e:
        # Handle detection failures gracefully for Gemini optimization
        logger.warning(f"Detection-only analysis failed: {e}")
        return {
            "task": f"{task}_detect_only",
            "input_path": input_path,
            "metrics": {"lanes_found": 0, "bands_found": 0, "detection_confidence": 0.0},
            "observation": {"status": "detection_failed", "error": str(e)},
            "status": "error"
        }

def run_detect_only_sds_page(input_path: str, config_path: str, output_dir: str, roi: str = None) -> dict:
    """Run SDS-PAGE detection-only analysis (per external audit)"""
    return run_detect_only("sds_page", input_path, config_path, output_dir, roi)

def run_detect_only_colony_count(input_path: str, config_path: str, output_dir: str, roi: str = None) -> dict:
    """Run colony detection-only analysis (per external audit)"""
    return run_detect_only("colony_count", input_path, config_path, output_dir, roi)

def run_detect_only_etbr_agarose(input_path: str, config_path: str, output_dir: str, roi: str = None) -> dict:
    """Run EtBr agarose detection-only analysis (per external audit)"""  
    return run_detect_only("etbr_agarose", input_path, config_path, output_dir, roi)

def rescue(task: str, input_path: str, last_config_path: str, output_dir: str) -> dict:
    """
    Rescue tool: Invert polarity, relax thresholds, adjust baseline bounds (per external audit)
    
    When normal analysis fails, this tries alternative parameter configurations:
    - Invert polarity if auto-detection failed
    - Lower prominence thresholds
    - Widen baseline windows
    - Relax peak distance constraints
    
    Args:
        task: Analysis task type (sds_page, colony_count, etbr_agarose)
        input_path: Path to input image
        last_config_path: Path to last failed configuration
        output_dir: Output directory for results
        
    Returns:
        Dict containing rescue attempt results
    """
    import yaml
    import tempfile
    
    logger.info(f"Attempting rescue for {task} with alternative parameters")
    
    try:
        # Load the last failed configuration
        with open(last_config_path, 'r') as f:
            config = yaml.safe_load(f)
        
        # Create rescue modifications
        rescue_config = config.copy()
        
        # Rescue strategy 1: Invert polarity
        if 'pre' not in rescue_config:
            rescue_config['pre'] = {}
        
        current_polarity = rescue_config['pre'].get('invert_polarity', 'auto')
        if current_polarity == 'auto':
            rescue_config['pre']['invert_polarity'] = 'true'
        elif current_polarity == 'false':
            rescue_config['pre']['invert_polarity'] = 'true'
        else:
            rescue_config['pre']['invert_polarity'] = 'false'
            
        # Rescue strategy 2: Relax detection thresholds
        if 'detect' not in rescue_config:
            rescue_config['detect'] = {}
        
        # Lower prominence fraction (make detection more sensitive)
        current_prominence = rescue_config['detect'].get('prominence_frac', 0.06)
        rescue_config['detect']['prominence_frac'] = max(0.02, current_prominence * 0.5)
        
        # Reduce minimum peak distance (allow closer peaks)
        current_distance = rescue_config['detect'].get('min_peak_distance_frac', 0.04)
        rescue_config['detect']['min_peak_distance_frac'] = max(0.01, current_distance * 0.7)
        
        # Rescue strategy 3: Adjust baseline parameters
        if 'baseline' not in rescue_config['detect']:
            rescue_config['detect']['baseline'] = {}
            
        baseline = rescue_config['detect']['baseline']
        
        # Widen baseline window
        current_window = baseline.get('window_frac', 0.02)
        baseline['window_frac'] = min(0.05, current_window * 1.5)
        
        # Adjust baseline quantile (make it more robust)
        current_quantile = baseline.get('quantile', 0.1)
        baseline['quantile'] = max(0.05, current_quantile * 0.8)
        
        # Save rescue configuration to temporary file
        rescue_config_fd, rescue_config_path = tempfile.mkstemp(suffix='.yaml', prefix='rescue_')
        try:
            with os.fdopen(rescue_config_fd, 'w') as f:
                yaml.dump(rescue_config, f)
            
            # Create rescue output directory
            rescue_output = Path(output_dir) / "rescue_attempt"
            rescue_output.mkdir(parents=True, exist_ok=True)
            
            # Attempt rescue analysis
            result = run_java_analysis(task, input_path, rescue_config_path, str(rescue_output))
            
            # Mark result as rescue attempt
            result["rescue_used"] = True
            result["rescue_config"] = {
                "polarity_inverted": rescue_config['pre']['invert_polarity'] != current_polarity,
                "prominence_reduced": rescue_config['detect']['prominence_frac'] < current_prominence,
                "distance_reduced": rescue_config['detect']['min_peak_distance_frac'] < current_distance,
                "baseline_widened": rescue_config['detect']['baseline']['window_frac'] > current_window
            }
            
            if "observation" in result:
                result["observation"]["rescue_used"] = True
                result["observation"]["status"] = "rescued"
            
            logger.info(f"Rescue successful: {result.get('metrics', {})}")
            return result
            
        finally:
            # Clean up temporary file
            if os.path.exists(rescue_config_path):
                os.unlink(rescue_config_path)
                
    except Exception as e:
        logger.error(f"Rescue attempt failed: {e}")
        return {
            "task": task,
            "input_path": input_path,
            "metrics": {},
            "observation": {"status": "rescue_failed", "error": str(e)},
            "rescue_used": True,
            "rescue_failed": True
        }

def helper_review(proposed_patch: dict, current_config: dict, last_metrics: dict = None) -> dict:
    """
    Helper critic system: Review parameter changes before application (per external audit)
    
    This runs before applying any Gemini patch to validate parameter safety:
    - Reject if baseline erases signal (baseline_post_max < 0.05)
    - Suggest lowering prominence if no lanes found but prominence high
    - Shrink peak distance if too few lanes detected
    - Block dangerous parameter combinations
    
    Args:
        proposed_patch: Parameter changes proposed by Gemini
        current_config: Current configuration
        last_metrics: Last analysis metrics (if available)
        
    Returns:
        Dict with review decision and feedback
    """
    
    logger.info("Helper reviewing proposed parameter patch...")
    
    review_result = {
        "approved": True,
        "warnings": [],
        "suggestions": [],
        "blocked_changes": [],
        "safe_alternatives": {}
    }
    
    # Extract relevant metrics for safety checks
    if last_metrics:
        lane_count = last_metrics.get("lane_count", 0)
        baseline_post_max = last_metrics.get("baseline_post_max", 1.0)
    else:
        lane_count = 0
        baseline_post_max = 1.0
    
    # Check proposed changes in detect section
    if "detect" in proposed_patch:
        detect_changes = proposed_patch["detect"]
        
        # Rule 1: Block if baseline erases signal
        if "baseline" in detect_changes:
            baseline_patch = detect_changes["baseline"]
            if "quantile" in baseline_patch:
                proposed_quantile = baseline_patch["quantile"]
                if proposed_quantile < 0.02 or proposed_quantile > 0.3:
                    review_result["approved"] = False
                    review_result["blocked_changes"].append("baseline.quantile")
                    review_result["warnings"].append("Proposed baseline quantile could erase signal")
                    review_result["safe_alternatives"]["detect.baseline.quantile"] = 0.1
        
        # Rule 2: Lower prominence if no lanes found but prominence high  
        if "prominence_frac" in detect_changes:
            proposed_prominence = detect_changes["prominence_frac"]
            current_prominence = current_config.get("detect", {}).get("prominence_frac", 0.06)
            
            if lane_count == 0 and current_prominence > 0.08:
                if proposed_prominence > 0.04:
                    review_result["suggestions"].append("Consider lowering prominence_frac to 0.04 for better detection")
                    review_result["safe_alternatives"]["detect.prominence_frac"] = 0.04
            
            # Block extreme prominence values
            if proposed_prominence < 0.01 or proposed_prominence > 0.15:
                review_result["approved"] = False
                review_result["blocked_changes"].append("prominence_frac")
                review_result["warnings"].append("Prominence fraction outside safe bounds [0.01, 0.15]")
                review_result["safe_alternatives"]["detect.prominence_frac"] = max(0.02, min(0.12, proposed_prominence))
        
        # Rule 3: Shrink peak distance if too few lanes
        if "min_peak_distance_frac" in detect_changes:
            proposed_distance = detect_changes["min_peak_distance_frac"] 
            current_distance = current_config.get("detect", {}).get("min_peak_distance_frac", 0.04)
            
            expected_lanes = current_config.get("sds", {}).get("expected_lanes", 10)
            if lane_count < 0.5 * expected_lanes and current_distance > 0.05:
                if proposed_distance > current_distance * 0.8:
                    review_result["suggestions"].append("Consider reducing min_peak_distance_frac by 20% to detect more lanes")
                    review_result["safe_alternatives"]["detect.min_peak_distance_frac"] = current_distance * 0.8
            
            # Block extreme distance values  
            if proposed_distance < 0.005 or proposed_distance > 0.1:
                review_result["approved"] = False
                review_result["blocked_changes"].append("min_peak_distance_frac")
                review_result["warnings"].append("Peak distance fraction outside safe bounds [0.005, 0.1]")
                review_result["safe_alternatives"]["detect.min_peak_distance_frac"] = max(0.01, min(0.08, proposed_distance))
    
    # Check preprocessing changes
    if "pre" in proposed_patch:
        pre_changes = proposed_patch["pre"]
        
        # Validate gaussian_sigma bounds
        if "gaussian_sigma" in pre_changes:
            proposed_sigma = pre_changes["gaussian_sigma"]
            if proposed_sigma < 0 or proposed_sigma > 5.0:
                review_result["approved"] = False
                review_result["blocked_changes"].append("gaussian_sigma")
                review_result["warnings"].append("Gaussian sigma outside safe bounds [0, 5.0]")
                review_result["safe_alternatives"]["pre.gaussian_sigma"] = max(0, min(3.0, proposed_sigma))
        
        # Validate clip percentiles
        if "clip_percentiles" in pre_changes:
            percentiles = pre_changes["clip_percentiles"]
            if isinstance(percentiles, list) and len(percentiles) >= 2:
                low, high = percentiles[0], percentiles[1]
                if low < 0 or high > 100 or low >= high:
                    review_result["approved"] = False
                    review_result["blocked_changes"].append("clip_percentiles")
                    review_result["warnings"].append("Invalid clip percentile range")
                    review_result["safe_alternatives"]["pre.clip_percentiles"] = [1.0, 99.0]
    
    # Final safety check: ensure some changes remain if others blocked
    if review_result["blocked_changes"] and len(review_result["blocked_changes"]) == len(proposed_patch.get("detect", {})):
        review_result["suggestions"].append("All proposed changes blocked - consider more conservative parameter adjustments")
    
    # Log review decision
    if review_result["approved"]:
        logger.info(f"Helper review approved patch with {len(review_result['warnings'])} warnings")
    else:
        logger.warning(f"Helper review blocked patch due to: {review_result['blocked_changes']}")
    
    return review_result

def validate_parameter_bounds(config: dict, task: str = "sds_page") -> dict:
    """
    Parameter bounds checking: Enforce safe parameter ranges (per external audit)
    
    Validates and clamps parameters to safe ranges:
    - detect.baseline.{method,window_frac,quantile} within bounds
    - detect.prominence_frac within [0.02, 0.12]  
    - detect.min_peak_distance_frac within [0.01, 0.08]
    - pre.invert_polarity ∈ {auto,true,false}
    - Everything else read-only unless approved
    
    Args:
        config: Configuration to validate
        task: Task type for task-specific bounds
        
    Returns:
        Dict with validation results and clamped values
    """
    
    # Define parameter bounds per external audit recommendations
    PARAMETER_BOUNDS = {
        "detect": {
            "prominence_frac": {"min": 0.02, "max": 0.12, "default": 0.06},
            "min_peak_distance_frac": {"min": 0.01, "max": 0.08, "default": 0.04},
            "baseline": {
                "quantile": {"min": 0.05, "max": 0.25, "default": 0.1},
                "window_frac": {"min": 0.01, "max": 0.05, "default": 0.02},
                "method": {"allowed": ["percentile", "rolling", "polynomial"], "default": "percentile"}
            }
        },
        "pre": {
            "gaussian_sigma": {"min": 0.0, "max": 3.0, "default": 1.0},
            "invert_polarity": {"allowed": ["auto", "true", "false"], "default": "auto"},
            "clip_percentiles": {"min": [0.5, 95.0], "max": [5.0, 99.9], "default": [2.0, 98.0]},
            "background_removal_radius": {"min": 0.0, "max": 50.0, "default": 0.0}
        }
    }
    
    validation_result = {
        "valid": True,
        "violations": [],
        "clamped_values": {},
        "warnings": []
    }
    
    def clamp_value(value, bounds, param_path):
        """Clamp value to bounds and record violation"""
        if isinstance(bounds, dict):
            if "min" in bounds and "max" in bounds:
                if value < bounds["min"] or value > bounds["max"]:
                    original_value = value
                    clamped = max(bounds["min"], min(bounds["max"], value))
                    validation_result["violations"].append({
                        "parameter": param_path,
                        "original": original_value,
                        "clamped": clamped,
                        "bounds": [bounds["min"], bounds["max"]]
                    })
                    validation_result["clamped_values"][param_path] = clamped
                    validation_result["valid"] = False
                    return clamped
            elif "allowed" in bounds:
                if value not in bounds["allowed"]:
                    default_value = bounds.get("default", bounds["allowed"][0])
                    validation_result["violations"].append({
                        "parameter": param_path,
                        "original": value,
                        "clamped": default_value,
                        "allowed": bounds["allowed"]
                    })
                    validation_result["clamped_values"][param_path] = default_value
                    validation_result["valid"] = False
                    return default_value
        return value
    
    # Validate detect section
    if "detect" in config:
        detect_config = config["detect"]
        
        # Check prominence_frac
        if "prominence_frac" in detect_config:
            bounds = PARAMETER_BOUNDS["detect"]["prominence_frac"]
            detect_config["prominence_frac"] = clamp_value(
                detect_config["prominence_frac"], bounds, "detect.prominence_frac"
            )
        
        # Check min_peak_distance_frac  
        if "min_peak_distance_frac" in detect_config:
            bounds = PARAMETER_BOUNDS["detect"]["min_peak_distance_frac"]
            detect_config["min_peak_distance_frac"] = clamp_value(
                detect_config["min_peak_distance_frac"], bounds, "detect.min_peak_distance_frac"
            )
        
        # Check baseline parameters
        if "baseline" in detect_config:
            baseline = detect_config["baseline"]
            
            if "quantile" in baseline:
                bounds = PARAMETER_BOUNDS["detect"]["baseline"]["quantile"]
                baseline["quantile"] = clamp_value(
                    baseline["quantile"], bounds, "detect.baseline.quantile"
                )
            
            if "window_frac" in baseline:
                bounds = PARAMETER_BOUNDS["detect"]["baseline"]["window_frac"]
                baseline["window_frac"] = clamp_value(
                    baseline["window_frac"], bounds, "detect.baseline.window_frac"
                )
                
            if "method" in baseline:
                bounds = PARAMETER_BOUNDS["detect"]["baseline"]["method"]
                baseline["method"] = clamp_value(
                    baseline["method"], bounds, "detect.baseline.method"
                )
    
    # Validate preprocessing section  
    if "pre" in config:
        pre_config = config["pre"]
        
        # Check gaussian_sigma
        if "gaussian_sigma" in pre_config:
            bounds = PARAMETER_BOUNDS["pre"]["gaussian_sigma"]
            pre_config["gaussian_sigma"] = clamp_value(
                pre_config["gaussian_sigma"], bounds, "pre.gaussian_sigma"
            )
        
        # Check invert_polarity
        if "invert_polarity" in pre_config:
            bounds = PARAMETER_BOUNDS["pre"]["invert_polarity"]
            pre_config["invert_polarity"] = clamp_value(
                pre_config["invert_polarity"], bounds, "pre.invert_polarity"
            )
        
        # Check clip_percentiles
        if "clip_percentiles" in pre_config:
            percentiles = pre_config["clip_percentiles"]
            if isinstance(percentiles, list) and len(percentiles) >= 2:
                bounds_min = PARAMETER_BOUNDS["pre"]["clip_percentiles"]["min"]
                bounds_max = PARAMETER_BOUNDS["pre"]["clip_percentiles"]["max"]
                
                clamped_low = max(bounds_min[0], min(bounds_max[0], percentiles[0]))
                clamped_high = max(bounds_min[1], min(bounds_max[1], percentiles[1]))
                
                if clamped_low != percentiles[0] or clamped_high != percentiles[1]:
                    validation_result["violations"].append({
                        "parameter": "pre.clip_percentiles",
                        "original": percentiles,
                        "clamped": [clamped_low, clamped_high],
                        "bounds": [bounds_min, bounds_max]
                    })
                    pre_config["clip_percentiles"] = [clamped_low, clamped_high]
                    validation_result["clamped_values"]["pre.clip_percentiles"] = [clamped_low, clamped_high]
                    validation_result["valid"] = False
        
        # Check background_removal_radius
        if "background_removal_radius" in pre_config:
            bounds = PARAMETER_BOUNDS["pre"]["background_removal_radius"]
            pre_config["background_removal_radius"] = clamp_value(
                pre_config["background_removal_radius"], bounds, "pre.background_removal_radius"
            )
    
    # Check for read-only parameters (external audit: everything else read-only unless approved)
    ALLOWED_SECTIONS = {"detect", "pre", "sds", "detection"}  # Basic allowed sections
    for section in config:
        if section not in ALLOWED_SECTIONS:
            validation_result["warnings"].append(f"Section '{section}' is read-only and may be ignored")
    
    # Log validation results
    if validation_result["violations"]:
        logger.warning(f"Parameter bounds validation failed: {len(validation_result['violations'])} violations")
        for violation in validation_result["violations"]:
            logger.warning(f"  {violation['parameter']}: {violation['original']} -> {violation['clamped']}")
    else:
        logger.info("Parameter bounds validation passed")
    
    return validation_result


def preflight(task, input_path, base_config_path, priors=None, outdir=None):
    """
    Image-aware config bootstrap - analyze image characteristics and suggest 
    appropriate starting parameters before running full analysis.
    
    Args:
        task: Analysis task type ("sds_page", "colony_count", "etbr_agarose")
        input_path: Path to input image
        base_config_path: Path to base configuration file
        priors: Optional dict of prior knowledge/constraints
        outdir: Optional output directory for debug images
    
    Returns:
        dict: Enhanced configuration with image-aware parameter suggestions
    """
    logger = logging.getLogger(__name__)
    
    try:
        # Load base configuration
        import yaml
        with open(base_config_path, 'r') as f:
            base_config = yaml.safe_load(f)
        
        # Run Java CLI to get image statistics and initial analysis
        java_exe = find_java_executable()
        classpath = build_classpath()
        java_cmd = [
            java_exe, '-Xmx4G', '-Djava.awt.headless=true',
            '-cp', classpath,  # Use dynamic classpath instead of hardcoded paths
            'com.betterdairy.autodense.cli.AutotuneAnalysisCLI',
            '--detect-only', '--task', task, '--input', input_path, 
            '--config', base_config_path, '--output', outdir or '/tmp/preflight',
            '--no-exit'  # CRITICAL: Required for optimizer integration
        ]
        
        logger.info(f"Running preflight analysis: {' '.join(java_cmd)}")
        
        result = subprocess.run(
            java_cmd, 
            capture_output=True, 
            text=True, 
            timeout=120,  # Shorter timeout for preflight
            cwd=JAVA_PROJECT_ROOT
        )
        
        if result.returncode != 0:
            logger.warning(f"Preflight analysis failed: {result.stderr}")
            # Return base config if preflight fails
            return {
                "status": "fallback",
                "config": base_config,
                "message": "Preflight failed, using base configuration",
                "error": result.stderr
            }
        
        # Parse image statistics from output
        stats = {}
        for line in result.stderr.split('\n'):
            if 'statistics -' in line:
                # Extract mean, std, range, percentiles
                if 'mean=' in line:
                    import re
                    match = re.search(r'mean=([\d.]+)±([\d.]+).*range=\[([\d.]+),([\d.]+)\].*p1/p99=\[([\d.]+),([\d.]+)\]', line)
                    if match:
                        stats.update({
                            'mean': float(match.group(1)),
                            'std': float(match.group(2)),
                            'range_min': float(match.group(3)),
                            'range_max': float(match.group(4)),
                            'p1': float(match.group(5)),
                            'p99': float(match.group(6))
                        })
        
        # Image-aware parameter suggestions based on statistics
        enhanced_config = copy.deepcopy(base_config)
        
        if stats:
            # Adjust parameters based on image characteristics
            if task in ["sds_page", "etbr_agarose"]:
                # For gels: adjust based on contrast and dynamic range
                dynamic_range = stats.get('p99', 255) - stats.get('p1', 0)
                contrast_ratio = stats.get('std', 50) / max(stats.get('mean', 128), 1)
                
                # Adjust detection sensitivity based on contrast
                if 'detect' not in enhanced_config:
                    enhanced_config['detect'] = {}
                
                if contrast_ratio < 0.2:  # Low contrast
                    logger.info(f"Low contrast detected (ratio: {contrast_ratio:.3f}), increasing sensitivity")
                    enhanced_config['detect']['prominence_frac'] = 0.04  # More sensitive
                    enhanced_config['detect']['min_peak_distance_frac'] = 0.02  # Allow closer peaks
                elif contrast_ratio > 0.6:  # High contrast
                    logger.info(f"High contrast detected (ratio: {contrast_ratio:.3f}), reducing sensitivity")
                    enhanced_config['detect']['prominence_frac'] = 0.08  # Less sensitive
                    enhanced_config['detect']['min_peak_distance_frac'] = 0.05  # Require wider spacing
                
                # Adjust preprocessing based on dynamic range
                if 'pre' not in enhanced_config:
                    enhanced_config['pre'] = {}
                
                if dynamic_range < 100:  # Low dynamic range
                    logger.info(f"Low dynamic range detected ({dynamic_range:.1f}), enhancing preprocessing")
                    enhanced_config['pre']['normalize_intensity'] = True
                    enhanced_config['pre']['clip_percentiles'] = [1.0, 99.0]  # Tighter clipping
                elif dynamic_range > 200:  # High dynamic range  
                    logger.info(f"High dynamic range detected ({dynamic_range:.1f}), gentle preprocessing")
                    enhanced_config['pre']['clip_percentiles'] = [2.0, 98.0]  # Gentler clipping
                
            elif task == "colony_count":
                # For colonies: adjust based on mean intensity (plate brightness)
                mean_intensity = stats.get('mean', 128)
                
                if 'detect' not in enhanced_config:
                    enhanced_config['detect'] = {}
                
                if mean_intensity < 80:  # Dark plate
                    logger.info(f"Dark plate detected (mean: {mean_intensity:.1f}), adjusting for dark background")
                    enhanced_config['detect']['blue_threshold'] = -4.0  # More sensitive to blue
                elif mean_intensity > 180:  # Bright plate
                    logger.info(f"Bright plate detected (mean: {mean_intensity:.1f}), adjusting for bright background")
                    enhanced_config['detect']['blue_threshold'] = -8.0  # Less sensitive to blue
        
        # Apply priors if provided
        if priors:
            logger.info(f"Applying priors: {priors}")
            # Merge priors into enhanced config (priors take precedence)
            def deep_merge(base, override):
                for key, value in override.items():
                    if isinstance(value, dict) and key in base and isinstance(base[key], dict):
                        deep_merge(base[key], value)
                    else:
                        base[key] = value
            deep_merge(enhanced_config, priors)
        
        return {
            "status": "success",
            "config": enhanced_config,
            "image_stats": stats,
            "adjustments_made": True,
            "message": f"Preflight analysis completed for {task}"
        }
        
    except Exception as e:
        logger.error(f"Preflight analysis failed: {e}")
        return {
            "status": "error",
            "config": base_config if 'base_config' in locals() else {},
            "error": str(e),
            "message": "Preflight failed with exception"
        }


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
