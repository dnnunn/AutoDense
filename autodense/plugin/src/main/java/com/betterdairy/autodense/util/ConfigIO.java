package com.betterdairy.autodense.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.yaml.snakeyaml.Yaml;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.io.*;

/**
 * Java-native config loader supporting both JSON and YAML - no Python required
 */
public final class ConfigIO {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private ConfigIO() {}
    
    @SuppressWarnings("unchecked")
    public static Map<String,Object> load(Path path) throws IOException {
        String name = path.getFileName().toString().toLowerCase();
        String txt = Files.readString(path, StandardCharsets.UTF_8);
        
        if (name.endsWith(".json")) {
            return MAPPER.readValue(txt, new TypeReference<Map<String,Object>>(){});
        }
        
        if (name.endsWith(".yaml") || name.endsWith(".yml")) {
            Object o = new Yaml().load(txt);
            if (o == null) return new LinkedHashMap<>();
            if (o instanceof Map) return (Map<String,Object>) o;
            throw new IOException("YAML root is not a map");
        }
        
        // Try JSON then YAML if extension missing
        try { 
            return MAPPER.readValue(txt, new TypeReference<Map<String,Object>>(){}); 
        } catch (Exception ignored) {}
        
        Object o = new Yaml().load(txt);
        if (o instanceof Map) return (Map<String,Object>) o;
        throw new IOException("Unrecognized config format for " + name);
    }
}