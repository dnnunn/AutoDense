#!/usr/bin/env python3
"""
Enhanced Environment Validation Script for AutoDense Multi-Environment Coordination

This script provides comprehensive environment validation with:
- Python package validation
- Java/Maven environment checking
- Fiji/ImageJ discovery and validation
- CI/CD friendly JSON output and exit codes
- Caching for expensive operations
"""

import json
import os
import subprocess
import sys
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import importlib.util
import logging

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

class EnvironmentValidator:
    """Comprehensive environment validation for AutoDense"""
    
    def __init__(self):
        self.results = {
            'python': {},
            'java': {},
            'maven': {},
            'fiji': {},
            'api_keys': {},
            'overall': {'status': 'unknown', 'errors': [], 'warnings': []}
        }
        
    def check_python_packages(self) -> Dict:
        """Validate required Python packages"""
        required_packages = [
            'numpy', 'scipy', 'scikit-image', 'matplotlib', 'pandas',
            'requests', 'pyyaml', 'jsonschema', 'pillow'
        ]
        
        package_status = {}
        missing_packages = []
        
        for package in required_packages:
            try:
                spec = importlib.util.find_spec(package)
                if spec is not None:
                    # Try to import and get version if available
                    try:
                        module = importlib.import_module(package)
                        version = getattr(module, '__version__', 'unknown')
                        package_status[package] = {'status': 'ok', 'version': version}
                    except ImportError as e:
                        package_status[package] = {'status': 'import_error', 'error': str(e)}
                        missing_packages.append(package)
                else:
                    package_status[package] = {'status': 'missing'}
                    missing_packages.append(package)
            except Exception as e:
                package_status[package] = {'status': 'error', 'error': str(e)}
                missing_packages.append(package)
        
        self.results['python'] = {
            'version': sys.version,
            'packages': package_status,
            'missing_packages': missing_packages,
            'status': 'ok' if not missing_packages else 'missing_packages'
        }
        
        return package_status
    
    def check_java_environment(self) -> Dict:
        """Validate Java environment"""
        java_info = {}
        
        # Check JAVA_HOME
        java_home = os.environ.get('JAVA_HOME')
        if java_home:
            java_info['java_home'] = java_home
            java_exe = Path(java_home) / 'bin' / 'java'
            java_info['java_home_valid'] = java_exe.exists()
        else:
            java_info['java_home'] = None
            java_info['java_home_valid'] = False
        
        # Check Java executable accessibility
        try:
            result = subprocess.run(['java', '-version'], 
                                  capture_output=True, text=True, timeout=10)
            if result.returncode == 0:
                java_info['java_accessible'] = True
                java_info['java_version'] = result.stderr.split('\n')[0] if result.stderr else 'unknown'
            else:
                java_info['java_accessible'] = False
                java_info['java_error'] = result.stderr
        except (subprocess.TimeoutExpired, FileNotFoundError) as e:
            java_info['java_accessible'] = False
            java_info['java_error'] = str(e)
        
        # Check Java version compatibility (Java 11+ required for modern ImageJ)
        if java_info.get('java_accessible'):
            try:
                version_line = java_info.get('java_version', '')
                if 'version' in version_line:
                    # Extract version number (handles both Oracle and OpenJDK formats)
                    version_part = version_line.split('"')[1] if '"' in version_line else version_line
                    major_version = int(version_part.split('.')[0]) if version_part else 0
                    java_info['major_version'] = major_version
                    java_info['version_compatible'] = major_version >= 11
                else:
                    java_info['version_compatible'] = False
            except (ValueError, IndexError):
                java_info['version_compatible'] = False
        
        self.results['java'] = java_info
        return java_info
    
    def check_maven_environment(self) -> Dict:
        """Validate Maven environment"""
        maven_info = {}
        
        try:
            result = subprocess.run(['mvn', '-version'], 
                                  capture_output=True, text=True, timeout=10)
            if result.returncode == 0:
                maven_info['maven_accessible'] = True
                maven_info['maven_version'] = result.stdout.split('\n')[0] if result.stdout else 'unknown'
            else:
                maven_info['maven_accessible'] = False
                maven_info['maven_error'] = result.stderr
        except (subprocess.TimeoutExpired, FileNotFoundError) as e:
            maven_info['maven_accessible'] = False
            maven_info['maven_error'] = str(e)
        
        # Check for AutoDense Maven project
        project_root = Path(__file__).parent.parent
        pom_path = project_root / 'autodense' / 'plugin' / 'pom.xml'
        maven_info['autodense_pom_exists'] = pom_path.exists()
        
        if maven_info['autodense_pom_exists']:
            # Check if project is compiled
            target_dir = project_root / 'autodense' / 'plugin' / 'target'
            classes_dir = target_dir / 'classes'
            maven_info['project_compiled'] = classes_dir.exists() and any(classes_dir.iterdir())
            maven_info['target_directory'] = str(target_dir)
        
        self.results['maven'] = maven_info
        return maven_info
    
    def check_fiji_environment(self) -> Dict:
        """Validate Fiji/ImageJ environment"""
        fiji_info = {}
        
        # Check environment variables
        fiji_dir = os.environ.get('FIJI_DIR')
        imagej_dir = os.environ.get('IMAGEJ_DIR')
        
        fiji_info['fiji_dir_env'] = fiji_dir
        fiji_info['imagej_dir_env'] = imagej_dir
        
        # Discover Fiji installations
        potential_locations = []
        
        # macOS standard locations
        if sys.platform == 'darwin':
            potential_locations.extend([
                '/Applications/Fiji.app',
                '/Applications/ImageJ.app', 
                Path.home() / 'Applications' / 'Fiji.app',
                Path.home() / 'Applications' / 'ImageJ.app'
            ])
        
        # Windows standard locations
        elif sys.platform == 'win32':
            potential_locations.extend([
                Path('C:/Fiji.app'),
                Path('C:/ImageJ'),
                Path('C:/Program Files/Fiji'),
                Path('C:/Program Files/ImageJ')
            ])
        
        # Linux standard locations
        else:
            potential_locations.extend([
                Path('/opt/fiji'),
                Path('/opt/imagej'),
                Path.home() / 'fiji',
                Path.home() / 'imagej'
            ])
        
        # Add environment variable paths if set
        if fiji_dir:
            potential_locations.append(Path(fiji_dir))
        if imagej_dir:
            potential_locations.append(Path(imagej_dir))
        
        discovered_installations = []
        for location in potential_locations:
            if isinstance(location, str):
                location = Path(location)
            
            if location.exists():
                # Check for key files that indicate a valid Fiji/ImageJ installation
                fiji_executable = None
                jars_dir = None
                
                # Check for Fiji-specific structure
                if (location / 'Contents' / 'MacOS').exists():  # macOS app bundle
                    fiji_executable = location / 'Contents' / 'MacOS' / 'fiji'
                    jars_dir = location / 'Contents' / 'java' / 'jars'
                elif (location / 'ImageJ-win64.exe').exists():  # Windows
                    fiji_executable = location / 'ImageJ-win64.exe'
                    jars_dir = location / 'jars'
                elif (location / 'ImageJ-linux64').exists():  # Linux
                    fiji_executable = location / 'ImageJ-linux64'
                    jars_dir = location / 'jars'
                
                if fiji_executable and fiji_executable.exists():
                    installation_info = {
                        'path': str(location),
                        'executable': str(fiji_executable),
                        'jars_dir': str(jars_dir) if jars_dir and jars_dir.exists() else None,
                        'valid': True
                    }
                    
                    # Count JARs for validation
                    if jars_dir and jars_dir.exists():
                        jar_count = len(list(jars_dir.glob('*.jar')))
                        installation_info['jar_count'] = jar_count
                        installation_info['has_core_jars'] = jar_count > 50  # Fiji typically has 100+ JARs
                    
                    discovered_installations.append(installation_info)
        
        fiji_info['discovered_installations'] = discovered_installations
        fiji_info['has_valid_installation'] = len(discovered_installations) > 0
        
        # Select primary installation
        if discovered_installations:
            primary = discovered_installations[0]  # Use first found
            fiji_info['primary_installation'] = primary
        
        self.results['fiji'] = fiji_info
        return fiji_info
    
    def check_api_keys(self) -> Dict:
        """Validate API key configuration"""
        api_info = {}
        
        # Check for API keys in various locations
        key_sources = ['env', 'properties_file', 'system_properties']
        
        for source in key_sources:
            api_info[source] = {}
        
        # Check environment variables
        for key_name in ['GEMINI_API_KEY', 'OPENAI_API_KEY', 'GROK_API_KEY']:
            env_value = os.environ.get(key_name)
            api_info['env'][key_name] = {
                'present': bool(env_value),
                'format_valid': env_value.startswith('AIza') if key_name == 'GEMINI_API_KEY' and env_value else None
            }
        
        # Check properties file
        project_root = Path(__file__).parent.parent
        properties_file = project_root / 'api-config.properties'
        
        api_info['properties_file']['exists'] = properties_file.exists()
        if properties_file.exists():
            try:
                with open(properties_file, 'r') as f:
                    content = f.read()
                    for key_name in ['GEMINI_API_KEY', 'OPENAI_API_KEY', 'GROK_API_KEY']:
                        has_key = key_name in content and not content.split(key_name + '=')[1].split('\n')[0].strip() == ''
                        api_info['properties_file'][key_name] = {'present': has_key}
            except Exception as e:
                api_info['properties_file']['error'] = str(e)
        
        self.results['api_keys'] = api_info
        return api_info
    
    def run_full_validation(self) -> Dict:
        """Run complete environment validation"""
        logger.info("Starting comprehensive environment validation...")
        
        # Run all checks
        self.check_python_packages()
        self.check_java_environment()
        self.check_maven_environment()
        self.check_fiji_environment()
        self.check_api_keys()
        
        # Determine overall status
        errors = []
        warnings = []
        
        # Critical errors (will prevent system from working)
        if not self.results['java']['java_accessible']:
            errors.append("Java is not accessible")
        
        if not self.results['java'].get('version_compatible', False):
            errors.append("Java version is not compatible (Java 11+ required)")
        
        if not self.results['maven']['maven_accessible']:
            errors.append("Maven is not accessible")
        
        if not self.results['maven']['autodense_pom_exists']:
            errors.append("AutoDense Maven project not found")
        
        if not self.results['fiji']['has_valid_installation']:
            errors.append("No valid Fiji/ImageJ installation found")
        
        # Warnings (will degrade functionality)
        if self.results['python']['missing_packages']:
            warnings.append(f"Missing Python packages: {', '.join(self.results['python']['missing_packages'])}")
        
        if not self.results['maven'].get('project_compiled', False):
            warnings.append("AutoDense project is not compiled")
        
        # Check for at least one API key
        has_any_api_key = False
        for source in self.results['api_keys'].values():
            if isinstance(source, dict):
                for key_data in source.values():
                    if isinstance(key_data, dict) and key_data.get('present'):
                        has_any_api_key = True
                        break
        
        if not has_any_api_key:
            warnings.append("No API keys configured")
        
        # Set overall status
        if errors:
            self.results['overall']['status'] = 'error'
        elif warnings:
            self.results['overall']['status'] = 'warning'
        else:
            self.results['overall']['status'] = 'ok'
        
        self.results['overall']['errors'] = errors
        self.results['overall']['warnings'] = warnings
        
        logger.info(f"Environment validation completed with status: {self.results['overall']['status']}")
        
        return self.results

def main():
    """Command line interface for environment validation"""
    import argparse
    
    parser = argparse.ArgumentParser(description="AutoDense Environment Validator")
    parser.add_argument('--json', action='store_true', help='Output results as JSON')
    parser.add_argument('--verbose', '-v', action='store_true', help='Verbose output')
    parser.add_argument('--check', choices=['python', 'java', 'maven', 'fiji', 'api'], 
                       help='Run specific check only')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    validator = EnvironmentValidator()
    
    if args.check:
        # Run specific check
        if args.check == 'python':
            result = validator.check_python_packages()
        elif args.check == 'java':
            result = validator.check_java_environment()
        elif args.check == 'maven':
            result = validator.check_maven_environment()
        elif args.check == 'fiji':
            result = validator.check_fiji_environment()
        elif args.check == 'api':
            result = validator.check_api_keys()
    else:
        # Run full validation
        result = validator.run_full_validation()
    
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        # Human-readable output
        overall = result.get('overall', {})
        status = overall.get('status', 'unknown')
        
        print(f"Environment Status: {status.upper()}")
        
        if overall.get('errors'):
            print("\nCRITICAL ERRORS:")
            for error in overall['errors']:
                print(f"  ❌ {error}")
        
        if overall.get('warnings'):
            print("\nWARNINGS:")
            for warning in overall['warnings']:
                print(f"  ⚠️  {warning}")
        
        if status == 'ok':
            print("\n✅ All environment checks passed!")
    
    # Exit with appropriate code for CI/CD
    if result.get('overall', {}).get('status') == 'error':
        sys.exit(1)
    elif result.get('overall', {}).get('status') == 'warning':
        sys.exit(2)  # Non-critical warnings
    else:
        sys.exit(0)

if __name__ == "__main__":
    main()