"""
Config Single Source of Truth with Precedence Hierarchy

Implements fail-fast config management with:
- Precedence: defaults.yml → priors.json → preflight.yaml → user config → CLI flags
- Deep merge with conflict resolution
- Required key validation
- Config fingerprinting for provenance
"""

import json
import yaml
import hashlib
from pathlib import Path
from typing import Dict, Any, List, Optional, Union
import logging

logger = logging.getLogger(__name__)

def deep_merge(base: Dict[str, Any], override: Dict[str, Any]) -> Dict[str, Any]:
    """
    Deep merge two dictionaries with override taking precedence.
    
    Args:
        base: Base configuration (lower precedence)
        override: Override configuration (higher precedence)
        
    Returns:
        Merged dictionary
    """
    result = dict(base)
    for key, value in override.items():
        if (isinstance(value, dict) and 
            isinstance(result.get(key), dict)):
            result[key] = deep_merge(result[key], value)
        else:
            result[key] = value
    return result

def get_nested_value(data: Dict[str, Any], path: str) -> Any:
    """
    Get nested dictionary value using dot notation.
    
    Args:
        data: Dictionary to search
        path: Dot-separated path (e.g., "pre.invert_polarity")
        
    Returns:
        Value at path or None if not found
    """
    keys = path.split('.')
    current = data
    
    for key in keys:
        if not isinstance(current, dict) or key not in current:
            return None
        current = current[key]
    
    return current

def config_fingerprint(config: Dict[str, Any]) -> str:
    """
    Generate SHA1 fingerprint of configuration for provenance tracking.
    
    Args:
        config: Configuration dictionary
        
    Returns:
        12-character hexadecimal fingerprint
    """
    canonical = json.dumps(config, sort_keys=True, separators=(',', ':'))
    return hashlib.sha1(canonical.encode('utf-8')).hexdigest()[:12]

class ConfigManager:
    """
    Single source of truth config manager with precedence hierarchy.
    """
    
    def __init__(self, 
                 project_root: Union[str, Path],
                 required_keys: Optional[List[str]] = None):
        """
        Initialize config manager.
        
        Args:
            project_root: Root directory containing config files
            required_keys: List of required config keys (dot notation)
        """
        self.project_root = Path(project_root)
        self.required_keys = required_keys or []
        self.config_cache = {}
        
    def load_config(self,
                   user_config_path: Optional[Union[str, Path]] = None,
                   cli_overrides: Optional[Dict[str, Any]] = None,
                   validate: bool = True) -> Dict[str, Any]:
        """
        Load configuration with full precedence hierarchy.
        
        Precedence (weakest → strongest):
        1. defaults.yml
        2. priors.json  
        3. preflight.yaml
        4. user_config_path
        5. cli_overrides
        
        Args:
            user_config_path: Path to user configuration file
            cli_overrides: CLI argument overrides
            validate: Whether to validate required keys
            
        Returns:
            Merged configuration dictionary
            
        Raises:
            ValueError: If required keys are missing
            FileNotFoundError: If config files are missing
        """
        config = {}
        
        # 1. Load defaults.yml
        defaults_path = self.project_root / "configs" / "defaults.yml"
        if defaults_path.exists():
            logger.debug(f"Loading defaults from {defaults_path}")
            config = deep_merge(config, self._load_yaml(defaults_path))
        
        # 2. Load priors.json
        priors_path = self.project_root / "configs" / "priors.json"
        if priors_path.exists():
            logger.debug(f"Loading priors from {priors_path}")
            config = deep_merge(config, self._load_json(priors_path))
            
        # 3. Load preflight.yaml
        preflight_path = self.project_root / "configs" / "preflight.yaml"
        if preflight_path.exists():
            logger.debug(f"Loading preflight from {preflight_path}")
            config = deep_merge(config, self._load_yaml(preflight_path))
            
        # 4. Load user config
        if user_config_path:
            user_path = Path(user_config_path)
            if not user_path.exists():
                raise FileNotFoundError(f"User config not found: {user_path}")
            
            logger.debug(f"Loading user config from {user_path}")
            if user_path.suffix.lower() in ('.yml', '.yaml'):
                config = deep_merge(config, self._load_yaml(user_path))
            elif user_path.suffix.lower() == '.json':
                config = deep_merge(config, self._load_json(user_path))
            else:
                raise ValueError(f"Unsupported config format: {user_path.suffix}")
        
        # 5. Apply CLI overrides
        if cli_overrides:
            logger.debug(f"Applying CLI overrides: {list(cli_overrides.keys())}")
            config = deep_merge(config, cli_overrides)
            
        # Validate required keys
        if validate:
            self._validate_required_keys(config)
            
        # Cache final config
        fingerprint = config_fingerprint(config)
        self.config_cache[fingerprint] = config.copy()
        
        logger.info(f"Config loaded successfully (fingerprint: {fingerprint})")
        return config
    
    def _load_yaml(self, path: Path) -> Dict[str, Any]:
        """Load YAML configuration file."""
        try:
            with open(path, 'r', encoding='utf-8') as f:
                return yaml.safe_load(f) or {}
        except Exception as e:
            raise ValueError(f"Failed to load YAML config {path}: {e}")
    
    def _load_json(self, path: Path) -> Dict[str, Any]:
        """Load JSON configuration file."""
        try:
            with open(path, 'r', encoding='utf-8') as f:
                return json.load(f)
        except Exception as e:
            raise ValueError(f"Failed to load JSON config {path}: {e}")
    
    def _validate_required_keys(self, config: Dict[str, Any]) -> None:
        """
        Validate that all required keys exist in config.
        
        Args:
            config: Configuration to validate
            
        Raises:
            ValueError: If required keys are missing
        """
        missing_keys = []
        
        for key_path in self.required_keys:
            if get_nested_value(config, key_path) is None:
                missing_keys.append(key_path)
        
        if missing_keys:
            raise ValueError(
                f"Missing required config keys: {missing_keys}. "
                f"Check your configuration hierarchy: defaults.yml → priors.json → "
                f"preflight.yaml → user config → CLI flags"
            )

# Required keys for different workflow types
REQUIRED_KEYS = {
    "preprocessing": [
        "pre.invert_polarity",
        "pre.clip_percentiles",
        "pre.gaussian_sigma"
    ],
    "detection": [
        "detect.prominence_frac",
        "detect.min_peak_distance_px"
    ],
    "gel_analysis": [
        "pre.background_removal_radius",
        "detect.prominence_frac",
        "pre.enable_deskew"
    ],
    "colony_analysis": [
        "detect.threshold_radius",
        "detect.min_colony_size"
    ]
}

def create_config_manager(project_root: Union[str, Path], 
                         workflow_type: str = "preprocessing") -> ConfigManager:
    """
    Factory function to create configured ConfigManager.
    
    Args:
        project_root: Project root directory
        workflow_type: Type of workflow (preprocessing, gel_analysis, etc.)
        
    Returns:
        Configured ConfigManager instance
    """
    required_keys = REQUIRED_KEYS.get(workflow_type, REQUIRED_KEYS["preprocessing"])
    return ConfigManager(project_root, required_keys)