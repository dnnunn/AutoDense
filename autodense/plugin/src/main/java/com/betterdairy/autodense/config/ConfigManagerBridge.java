package com.betterdairy.autodense.config;

import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Java bridge to the Python ConfigManager with fail-fast validation.
 * 
 * Provides integration between Java analysis workflows and Python configuration
 * management system with precedence hierarchy and required key validation.
 * 
 * Config precedence (weakest → strongest):
 * 1. defaults.yml
 * 2. priors.json  
 * 3. preflight.yaml
 * 4. user_config_path
 * 5. cli_overrides
 */
public class ConfigManagerBridge {
    
    private static final Logger logger = Logger.getLogger(ConfigManagerBridge.class.getName());
    
    private final Path projectRoot;
    private final String workflowType;
    
    /**
     * Initialize config manager bridge.
     * 
     * @param projectRoot Root directory containing config files
     * @param workflowType Type of workflow (preprocessing, gel_analysis, colony_analysis, detection)
     */
    public ConfigManagerBridge(Path projectRoot, String workflowType) {
        this.projectRoot = projectRoot;
        this.workflowType = workflowType;
    }
    
    /**
     * Load and validate configuration with full precedence hierarchy and fail-fast validation.
     * 
     * @param userConfigPath Path to user configuration file (optional)
     * @param cliOverrides CLI argument overrides as JSON string (optional)
     * @return Validated and merged configuration
     * @throws ConfigurationException If required keys are missing or config loading fails
     */
    public JSONObject loadValidatedConfig(String userConfigPath, String cliOverrides) 
            throws ConfigurationException {
        
        try {
            // Build Python command
            List<String> command = new ArrayList<>();
            command.add("python3");
            command.add("-c");
            
            // Python script that imports and uses the config_manager
            StringBuilder pythonScript = new StringBuilder();
            pythonScript.append("import sys; import json; ");
            pythonScript.append("sys.path.insert(0, '").append(projectRoot.resolve("autodense_autotune")).append("'); ");
            pythonScript.append("from config_manager import create_config_manager; ");
            pythonScript.append("manager = create_config_manager('").append(projectRoot).append("', '").append(workflowType).append("'); ");
            
            // Handle optional parameters
            if (userConfigPath != null && !userConfigPath.isEmpty()) {
                pythonScript.append("user_config = '").append(userConfigPath).append("'; ");
            } else {
                pythonScript.append("user_config = None; ");
            }
            
            if (cliOverrides != null && !cliOverrides.isEmpty()) {
                pythonScript.append("cli_overrides = json.loads('''").append(cliOverrides).append("'''); ");
            } else {
                pythonScript.append("cli_overrides = None; ");
            }
            
            // Load config with validation
            pythonScript.append("try: ");
            pythonScript.append("  config = manager.load_config(user_config, cli_overrides, validate=True); ");
            pythonScript.append("  print(json.dumps(config, sort_keys=True)); ");
            pythonScript.append("except Exception as e: ");
            pythonScript.append("  print('ERROR:' + str(e), file=sys.stderr); ");
            pythonScript.append("  sys.exit(1)");
            
            command.add(pythonScript.toString());
            
            logger.info("Loading config via Python ConfigManager for workflow: " + workflowType);
            
            // Execute Python script
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectRoot.toFile());
            Process process = pb.start();
            
            // Read output
            StringBuilder output = new StringBuilder();
            StringBuilder errors = new StringBuilder();
            
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                 BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
                
                while ((line = errorReader.readLine()) != null) {
                    errors.append(line).append("\n");
                }
            }
            
            // Wait for completion
            int exitCode = process.waitFor();
            
            if (exitCode != 0) {
                String errorMsg = errors.toString();
                if (errorMsg.startsWith("ERROR:")) {
                    // Extract the actual error message
                    errorMsg = errorMsg.substring(6).trim();
                }
                throw new ConfigurationException("Config validation failed: " + errorMsg);
            }
            
            // Parse the JSON output
            String configJson = output.toString().trim();
            if (configJson.isEmpty()) {
                throw new ConfigurationException("No configuration returned from Python ConfigManager");
            }
            
            JSONObject config = new JSONObject(configJson);
            
            // Extract fingerprint if available
            String fingerprint = extractFingerprint(config);
            if (fingerprint != null) {
                logger.info("Config loaded and validated successfully (fingerprint: " + fingerprint + ")");
            } else {
                logger.info("Config loaded and validated successfully");
            }
            
            return config;
            
        } catch (IOException | InterruptedException e) {
            throw new ConfigurationException("Failed to execute Python ConfigManager: " + e.getMessage(), e);
        }
    }
    
    /**
     * Load configuration without validation (fallback for compatibility).
     * 
     * @param userConfigPath Path to user configuration file
     * @return Configuration without validation
     * @throws ConfigurationException If config loading fails
     */
    public JSONObject loadConfigNoValidation(String userConfigPath) throws ConfigurationException {
        try {
            // Build simpler Python command without validation
            List<String> command = new ArrayList<>();
            command.add("python3");
            command.add("-c");
            
            StringBuilder pythonScript = new StringBuilder();
            pythonScript.append("import sys; import json; ");
            pythonScript.append("sys.path.insert(0, '").append(projectRoot.resolve("autodense_autotune")).append("'); ");
            pythonScript.append("from config_manager import create_config_manager; ");
            pythonScript.append("manager = create_config_manager('").append(projectRoot).append("', '").append(workflowType).append("'); ");
            pythonScript.append("user_config = '").append(userConfigPath != null ? userConfigPath : "").append("' if '").append(userConfigPath != null ? userConfigPath : "").append("' else None; ");
            pythonScript.append("config = manager.load_config(user_config, None, validate=False); ");
            pythonScript.append("print(json.dumps(config, sort_keys=True))");
            
            command.add(pythonScript.toString());
            
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.directory(projectRoot.toFile());
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new ConfigurationException("Failed to load config without validation");
            }
            
            return new JSONObject(output.toString().trim());
            
        } catch (IOException | InterruptedException e) {
            throw new ConfigurationException("Failed to load config: " + e.getMessage(), e);
        }
    }
    
    /**
     * Extract config fingerprint from loaded configuration if available.
     */
    private String extractFingerprint(JSONObject config) {
        try {
            // Calculate fingerprint using same method as Python config_manager
            List<String> command = new ArrayList<>();
            command.add("python3");
            command.add("-c");
            
            String pythonScript = String.format(
                "import sys; import json; " +
                "sys.path.insert(0, '%s'); " +
                "from config_manager import config_fingerprint; " +
                "config = json.loads('''%s'''); " +
                "print(config_fingerprint(config))",
                projectRoot.resolve("autodense_autotune"),
                config.toString()
            );
            command.add(pythonScript);
            
            ProcessBuilder pb = new ProcessBuilder(command);
            Process process = pb.start();
            
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line);
                }
            }
            
            if (process.waitFor() == 0) {
                return output.toString().trim();
            }
            
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to calculate config fingerprint", e);
        }
        return null;
    }
    
    /**
     * Create config manager for specific workflow type.
     * 
     * @param projectRoot Project root directory
     * @param workflowType Workflow type (preprocessing, gel_analysis, colony_analysis, detection)
     * @return ConfigManagerBridge instance
     */
    public static ConfigManagerBridge forWorkflow(String projectRoot, String workflowType) {
        return new ConfigManagerBridge(Paths.get(projectRoot), workflowType);
    }
    
    /**
     * Create config manager for current working directory.
     * 
     * @param workflowType Workflow type
     * @return ConfigManagerBridge instance
     */
    public static ConfigManagerBridge forCurrentDir(String workflowType) {
        return new ConfigManagerBridge(Paths.get(".").toAbsolutePath().normalize(), workflowType);
    }
}