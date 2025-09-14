# autodense/security/key_manager.py
"""
Secure API key management system for AutoDense.
CRITICAL SECURITY: Never store API keys in plaintext or commit to version control.
"""

import os
import logging
from typing import Optional
from pathlib import Path

# Configure secure logging (never log actual keys)
logger = logging.getLogger(__name__)
logger.setLevel(logging.WARNING)  # Only log warnings/errors for security


class SecureAPIKeyManager:
    """
    Secure API key storage and retrieval system.
    
    Security Features:
    - Environment variable priority
    - Masked key logging (never expose actual keys)  
    - Validation of key formats
    - Clear error messages without exposing sensitive data
    """
    
    def __init__(self):
        self.service_validators = {
            "openai": self._validate_openai_key,
            "gemini": self._validate_gemini_key,
        }
    
    def get_api_key(self, service: str) -> Optional[str]:
        """
        Retrieve API key for a service with security best practices.
        
        Priority order:
        1. Environment variable (most secure)
        2. User config directory (if implemented)
        3. Fail securely with clear instructions
        
        Args:
            service: Service name ('openai', 'gemini', etc.)
            
        Returns:
            API key if found and valid, None otherwise
            
        Security Notes:
            - Never logs actual API key values
            - Returns None on any error (fail securely)
            - Provides clear user guidance without exposing system internals
        """
        service = service.lower()
        
        # Try environment variable first (most secure)
        env_var = f"{service.upper()}_API_KEY"
        api_key = os.environ.get(env_var)
        
        if api_key:
            if self._validate_key_format(service, api_key):
                logger.info(f"✅ {service.upper()} API key loaded from environment")
                return api_key
            else:
                logger.error(f"❌ Invalid {service.upper()} API key format in environment variable {env_var}")
                return None
        
        # Try legacy config file (deprecated but supported)
        config_key = self._load_from_config_file(service)
        if config_key and config_key != "__SECURE_ENV_VARIABLE__":
            logger.warning(f"⚠️ Using {service.upper()} API key from config file (DEPRECATED - use environment variable)")
            if self._validate_key_format(service, config_key):
                return config_key
            else:
                logger.error(f"❌ Invalid {service.upper()} API key format in config file")
                return None
        
        # Fail securely with helpful instructions
        logger.error(f"❌ No valid {service.upper()} API key found")
        logger.error(f"💡 Set environment variable: export {env_var}=your_api_key_here")
        return None
    
    def _load_from_config_file(self, service: str) -> Optional[str]:
        """
        Load API key from config file (deprecated method).
        Only for backward compatibility.
        """
        try:
            config_file = Path(__file__).parent.parent.parent / "api-config.properties"
            if config_file.exists():
                with open(config_file, 'r') as f:
                    for line in f:
                        if line.startswith(f"{service.upper()}_API_KEY="):
                            key = line.split("=", 1)[1].strip()
                            # Don't return placeholder values
                            if key and not key.startswith("__") and not key.startswith("#"):
                                return key
        except Exception as e:
            logger.error(f"Error reading config file: {type(e).__name__}")
        return None
    
    def _validate_key_format(self, service: str, api_key: str) -> bool:
        """
        Validate API key format without exposing the actual key.
        
        Args:
            service: Service name
            api_key: API key to validate
            
        Returns:
            True if format is valid, False otherwise
        """
        if not api_key or len(api_key.strip()) == 0:
            return False
            
        # Use service-specific validators
        validator = self.service_validators.get(service)
        if validator:
            return validator(api_key)
        
        # Generic validation - must be reasonable length
        return 10 <= len(api_key) <= 200
    
    def _validate_openai_key(self, key: str) -> bool:
        """Validate OpenAI API key format without exposing the key."""
        # OpenAI keys start with 'sk-' and have specific length patterns
        if not key.startswith("sk-"):
            logger.error("❌ OpenAI API key must start with 'sk-'")
            return False
        
        if len(key) < 40:
            logger.error("❌ OpenAI API key appears too short")
            return False
            
        return True
    
    def _validate_gemini_key(self, key: str) -> bool:
        """Validate Gemini API key format without exposing the key."""
        # Gemini keys typically start with 'AIza'
        if not key.startswith("AIza"):
            logger.error("❌ Gemini API key must start with 'AIza'")
            return False
            
        if len(key) < 30:
            logger.error("❌ Gemini API key appears too short")
            return False
            
        return True
    
    def mask_key_for_logging(self, api_key: str) -> str:
        """
        Create a masked version of API key safe for logging.
        
        Args:
            api_key: Full API key
            
        Returns:
            Masked key showing only prefix and suffix
            
        Security: Never logs the full key, only enough to identify which key is being used
        """
        if not api_key or len(api_key) < 8:
            return "***INVALID***"
        
        # Show first 3 and last 3 characters only
        return f"{api_key[:3]}...{api_key[-3:]}"
    
    def is_placeholder_key(self, api_key: str) -> bool:
        """
        Check if the provided key is a placeholder/example key.
        
        Args:
            api_key: Key to check
            
        Returns:
            True if this is a placeholder key that should not be used
        """
        placeholders = [
            "__SECURE_ENV_VARIABLE__",
            "your_api_key_here", 
            "sk-XXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX",
            "AIzaXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXXX"
        ]
        
        return api_key in placeholders or api_key.startswith("__") or api_key.startswith("#")


# Global secure key manager instance
_key_manager = SecureAPIKeyManager()


def get_openai_api_key() -> Optional[str]:
    """
    Secure way to get OpenAI API key.
    
    Returns:
        Valid OpenAI API key or None if not available/invalid
        
    Usage:
        api_key = get_openai_api_key()
        if not api_key:
            raise ValueError("OpenAI API key not configured. Set OPENAI_API_KEY environment variable.")
    """
    return _key_manager.get_api_key("openai")


def get_gemini_api_key() -> Optional[str]:
    """
    Secure way to get Gemini API key.
    
    Returns:
        Valid Gemini API key or None if not available/invalid
    """
    return _key_manager.get_api_key("gemini")


def mask_key_for_logging(api_key: str) -> str:
    """
    Mask API key for safe logging.
    
    Args:
        api_key: Full API key
        
    Returns:
        Masked version safe for logs
    """
    return _key_manager.mask_key_for_logging(api_key)