package com.betterdairy.autodense.config;

/**
 * Exception thrown when configuration loading or validation fails.
 * 
 * Used by ConfigManagerBridge to provide clear error messages for:
 * - Missing required configuration keys
 * - Invalid configuration file formats
 * - Config file not found errors
 * - Validation constraint violations
 */
public class ConfigurationException extends Exception {
    
    /**
     * Create configuration exception with message.
     * 
     * @param message Error description
     */
    public ConfigurationException(String message) {
        super(message);
    }
    
    /**
     * Create configuration exception with message and cause.
     * 
     * @param message Error description
     * @param cause Underlying exception
     */
    public ConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}