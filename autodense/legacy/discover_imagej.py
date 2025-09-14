#!/usr/bin/env python3
"""
Fiji/ImageJ Discovery and Classpath Management for AutoDense

This script provides intelligent discovery and caching of Fiji/ImageJ installations
with comprehensive classpath building for the AutoDense optimization system.

Features:
- Automatic Fiji/ImageJ installation discovery across platforms
- Smart classpath caching to avoid expensive JAR scanning
- JSON output for programmatic integration
- Validation of installation completeness
"""

import json
import os
import sys
import hashlib
from pathlib import Path
from typing import Dict, List, Optional, Tuple
import logging
import time

# Configure logging
logging.basicConfig(level=logging.INFO, format='%(levelname)s: %(message)s')
logger = logging.getLogger(__name__)

class FijiDiscovery:
    """Intelligent Fiji/ImageJ discovery and classpath management"""
    
    def __init__(self, cache_dir: Optional[Path] = None):
        self.cache_dir = cache_dir or Path.home() / '.autodense' / 'cache'
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.cache_file = self.cache_dir / 'fiji_classpath_cache.json'
        
    def get_platform_search_paths(self) -> List[Path]:
        """Get platform-specific search paths for Fiji/ImageJ installations"""
        paths = []
        
        # Check environment variables first
        for env_var in ['FIJI_DIR', 'IMAGEJ_DIR', 'FIJI_HOME', 'IMAGEJ_HOME']:
            env_path = os.environ.get(env_var)
            if env_path:
                paths.append(Path(env_path))
        
        # Platform-specific standard locations
        if sys.platform == 'darwin':  # macOS
            paths.extend([
                Path('/Applications/Fiji.app'),
                Path('/Applications/ImageJ.app'),
                Path('/Applications/ImageJ2.app'),
                Path.home() / 'Applications' / 'Fiji.app',
                Path.home() / 'Applications' / 'ImageJ.app',
                Path.home() / 'Applications' / 'ImageJ2.app',
                Path('/opt/fiji'),
                Path('/opt/imagej'),
                Path.home() / 'fiji',
                Path.home() / 'imagej'
            ])
        
        elif sys.platform == 'win32':  # Windows
            paths.extend([
                Path('C:/Fiji.app'),
                Path('C:/ImageJ'),
                Path('C:/ImageJ2'),
                Path('C:/Program Files/Fiji'),
                Path('C:/Program Files/ImageJ'),
                Path('C:/Program Files/ImageJ2'),
                Path('C:/Program Files (x86)/Fiji'),
                Path('C:/Program Files (x86)/ImageJ'),
                Path.home() / 'fiji',
                Path.home() / 'imagej'
            ])
        
        else:  # Linux and other Unix-like systems
            paths.extend([
                Path('/opt/fiji'),
                Path('/opt/imagej'),
                Path('/opt/ImageJ'),
                Path('/usr/local/fiji'),
                Path('/usr/local/imagej'),
                Path.home() / 'fiji',
                Path.home() / 'imagej',
                Path.home() / 'Fiji.app',
                Path.home() / 'ImageJ',
                Path.home() / '.local' / 'share' / 'fiji',
                Path.home() / '.local' / 'share' / 'imagej'
            ])
        
        return paths
    
    def validate_installation(self, path: Path) -> Optional[Dict]:
        """Validate a potential Fiji/ImageJ installation"""
        if not path.exists() or not path.is_dir():
            return None
        
        installation_info = {
            'path': str(path),
            'type': 'unknown',
            'executable': None,
            'jars_dir': None,
            'plugins_dir': None,
            'jar_count': 0,
            'core_jars_found': [],
            'valid': False
        }
        
        # Detect installation type and structure
        if path.name.endswith('.app') and (path / 'Contents').exists():
            # macOS app bundle
            installation_info['type'] = 'macos_app'
            contents_dir = path / 'Contents'
            
            # Look for executable
            macos_dir = contents_dir / 'MacOS'
            if macos_dir.exists():
                for exec_name in ['fiji', 'imagej', 'ImageJ', 'fiji-macos']:
                    exec_path = macos_dir / exec_name
                    if exec_path.exists():
                        installation_info['executable'] = str(exec_path)
                        break
            
            # Look for JARs directory
            java_dir = contents_dir / 'java'
            resources_java = contents_dir / 'Resources' / 'java'
            
            if java_dir.exists():
                installation_info['jars_dir'] = str(java_dir / 'jars' if (java_dir / 'jars').exists() else java_dir)
                installation_info['plugins_dir'] = str(java_dir / 'plugins') if (java_dir / 'plugins').exists() else None
            elif resources_java.exists():
                installation_info['jars_dir'] = str(resources_java / 'jars' if (resources_java / 'jars').exists() else resources_java)
                installation_info['plugins_dir'] = str(resources_java / 'plugins') if (resources_java / 'plugins').exists() else None
        
        else:
            # Standard directory structure
            installation_info['type'] = 'standard'
            
            # Look for executable
            for exec_name in ['fiji', 'imagej', 'ImageJ', 'ImageJ-linux64', 'ImageJ-win64.exe']:
                exec_path = path / exec_name
                if exec_path.exists():
                    installation_info['executable'] = str(exec_path)
                    break
            
            # Look for JARs directory
            for jar_dir_name in ['jars', 'lib', 'plugins']:
                jar_dir = path / jar_dir_name
                if jar_dir.exists() and any(jar_dir.glob('*.jar')):
                    installation_info['jars_dir'] = str(jar_dir)
                    break
            
            # Look for plugins directory
            plugins_dir = path / 'plugins'
            if plugins_dir.exists():
                installation_info['plugins_dir'] = str(plugins_dir)
        
        # Validate JAR directory and count JARs
        if installation_info['jars_dir']:
            jars_path = Path(installation_info['jars_dir'])
            if jars_path.exists():
                jar_files = list(jars_path.glob('*.jar'))
                installation_info['jar_count'] = len(jar_files)
                
                # Look for core ImageJ JARs
                core_jars = ['ij.jar', 'imagej-', 'fiji-', 'scifio', 'bioformats']
                for jar_file in jar_files:
                    jar_name = jar_file.name.lower()
                    for core_jar in core_jars:
                        if core_jar in jar_name:
                            installation_info['core_jars_found'].append(jar_file.name)
        
        # Determine if installation is valid
        installation_info['valid'] = (
            installation_info['executable'] is not None and
            Path(installation_info['executable']).exists() and
            installation_info['jar_count'] > 10 and  # Fiji typically has 100+ JARs
            len(installation_info['core_jars_found']) > 0
        )
        
        return installation_info if installation_info['valid'] else None
    
    def discover_installations(self) -> List[Dict]:
        """Discover all valid Fiji/ImageJ installations"""
        logger.info("Discovering Fiji/ImageJ installations...")
        
        search_paths = self.get_platform_search_paths()
        installations = []
        
        for path in search_paths:
            logger.debug(f"Checking path: {path}")
            installation = self.validate_installation(path)
            if installation:
                logger.info(f"Found valid installation at: {path}")
                installations.append(installation)
        
        # Sort by jar count (more JARs = more complete installation)
        installations.sort(key=lambda x: x['jar_count'], reverse=True)
        
        logger.info(f"Discovered {len(installations)} valid installations")
        return installations
    
    def build_classpath(self, installation: Dict, include_plugins: bool = True) -> Tuple[str, List[str]]:
        """Build comprehensive classpath for an installation"""
        jars_dir = Path(installation['jars_dir'])
        jar_paths = []
        
        if jars_dir.exists():
            # Add all JARs from the main jars directory
            jar_files = list(jars_dir.glob('*.jar'))
            jar_paths.extend([str(jar) for jar in sorted(jar_files)])
        
        # Add plugins directory if requested and available
        if include_plugins and installation.get('plugins_dir'):
            plugins_dir = Path(installation['plugins_dir'])
            if plugins_dir.exists():
                plugin_jars = list(plugins_dir.rglob('*.jar'))
                jar_paths.extend([str(jar) for jar in sorted(plugin_jars)])
        
        # Platform-appropriate classpath separator
        separator = ';' if sys.platform == 'win32' else ':'
        classpath = separator.join(jar_paths)
        
        return classpath, jar_paths
    
    def get_cache_key(self, installation: Dict) -> str:
        """Generate cache key for an installation"""
        # Create hash from installation path and modification times of key directories
        key_data = installation['path']
        
        # Include modification times to detect changes
        try:
            jars_dir = Path(installation['jars_dir'])
            if jars_dir.exists():
                key_data += str(jars_dir.stat().st_mtime)
            
            if installation.get('plugins_dir'):
                plugins_dir = Path(installation['plugins_dir'])
                if plugins_dir.exists():
                    key_data += str(plugins_dir.stat().st_mtime)
        except OSError:
            pass
        
        return hashlib.sha256(key_data.encode()).hexdigest()[:12]
    
    def load_cached_classpath(self, installation: Dict) -> Optional[Dict]:
        """Load cached classpath data for an installation"""
        if not self.cache_file.exists():
            return None
        
        try:
            with open(self.cache_file, 'r') as f:
                cache_data = json.load(f)
            
            cache_key = self.get_cache_key(installation)
            if cache_key in cache_data:
                cached_entry = cache_data[cache_key]
                
                # Check if cache is still valid (not older than 24 hours)
                cache_time = cached_entry.get('timestamp', 0)
                if time.time() - cache_time < 24 * 3600:
                    logger.debug(f"Using cached classpath for {installation['path']}")
                    return cached_entry
        
        except (json.JSONDecodeError, KeyError, OSError) as e:
            logger.debug(f"Cache read error: {e}")
        
        return None
    
    def save_cached_classpath(self, installation: Dict, classpath_data: Dict):
        """Save classpath data to cache"""
        cache_data = {}
        
        # Load existing cache
        if self.cache_file.exists():
            try:
                with open(self.cache_file, 'r') as f:
                    cache_data = json.load(f)
            except (json.JSONDecodeError, OSError):
                pass
        
        # Add new entry
        cache_key = self.get_cache_key(installation)
        cache_data[cache_key] = {
            'timestamp': time.time(),
            'installation_path': installation['path'],
            **classpath_data
        }
        
        # Clean old entries (keep only last 10)
        if len(cache_data) > 10:
            sorted_entries = sorted(cache_data.items(), key=lambda x: x[1].get('timestamp', 0))
            cache_data = dict(sorted_entries[-10:])
        
        # Save cache
        try:
            with open(self.cache_file, 'w') as f:
                json.dump(cache_data, f, indent=2)
        except OSError as e:
            logger.warning(f"Failed to save cache: {e}")
    
    def get_classpath_for_installation(self, installation: Dict, include_plugins: bool = True) -> Dict:
        """Get classpath for installation with caching"""
        # Try to load from cache first
        cached_data = self.load_cached_classpath(installation)
        if cached_data:
            return {
                'classpath': cached_data['classpath'],
                'jar_paths': cached_data['jar_paths'],
                'jar_count': cached_data['jar_count'],
                'cached': True
            }
        
        # Build classpath
        logger.info(f"Building classpath for {installation['path']}")
        classpath, jar_paths = self.build_classpath(installation, include_plugins)
        
        classpath_data = {
            'classpath': classpath,
            'jar_paths': jar_paths,
            'jar_count': len(jar_paths),
            'cached': False
        }
        
        # Save to cache
        self.save_cached_classpath(installation, classpath_data)
        
        return classpath_data
    
    def discover_and_build_all_classpaths(self) -> Dict:
        """Discover installations and build classpaths for all"""
        installations = self.discover_installations()
        
        result = {
            'installations': [],
            'primary_installation': None,
            'primary_classpath': None
        }
        
        for installation in installations:
            classpath_data = self.get_classpath_for_installation(installation)
            
            installation_with_classpath = {
                **installation,
                **classpath_data
            }
            
            result['installations'].append(installation_with_classpath)
        
        # Set primary installation (first/best one found)
        if installations:
            primary = result['installations'][0]
            result['primary_installation'] = primary
            result['primary_classpath'] = primary['classpath']
        
        return result

def main():
    """Command line interface for Fiji discovery"""
    import argparse
    
    parser = argparse.ArgumentParser(description="Fiji/ImageJ Discovery and Classpath Builder")
    parser.add_argument('--json', action='store_true', help='Output results as JSON')
    parser.add_argument('--verbose', '-v', action='store_true', help='Verbose output')
    parser.add_argument('--no-plugins', action='store_true', help='Exclude plugins from classpath')
    parser.add_argument('--cache-dir', type=Path, help='Custom cache directory')
    parser.add_argument('--clear-cache', action='store_true', help='Clear existing cache')
    
    args = parser.parse_args()
    
    if args.verbose:
        logging.getLogger().setLevel(logging.DEBUG)
    
    # Initialize discovery
    discovery = FijiDiscovery(cache_dir=args.cache_dir)
    
    # Clear cache if requested
    if args.clear_cache:
        if discovery.cache_file.exists():
            discovery.cache_file.unlink()
            logger.info("Cache cleared")
    
    # Run discovery
    result = discovery.discover_and_build_all_classpaths()
    
    if args.json:
        print(json.dumps(result, indent=2))
    else:
        # Human-readable output
        installations = result['installations']
        print(f"Found {len(installations)} Fiji/ImageJ installations:")
        
        for i, installation in enumerate(installations):
            print(f"\n{i+1}. {installation['path']}")
            print(f"   Type: {installation['type']}")
            print(f"   Executable: {installation['executable']}")
            print(f"   JAR Count: {installation['jar_count']}")
            print(f"   Core JARs: {', '.join(installation['core_jars_found'][:3])}")
            if installation['cached']:
                print(f"   Classpath: Cached")
            else:
                print(f"   Classpath: Generated")
        
        if result['primary_installation']:
            print(f"\nPrimary installation: {result['primary_installation']['path']}")
            print(f"Classpath ready: {len(result['primary_classpath'].split(':' if sys.platform != 'win32' else ';'))} entries")
        else:
            print("\n❌ No valid installations found!")
            sys.exit(1)

if __name__ == "__main__":
    main()