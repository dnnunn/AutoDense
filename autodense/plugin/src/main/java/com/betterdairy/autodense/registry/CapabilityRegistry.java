package com.betterdairy.autodense.registry;

import com.betterdairy.autodense.tools.GelAnalysisTools;
import org.json.JSONObject;
import org.json.JSONArray;
import org.scijava.Context;
import org.scijava.plugin.PluginService;
import org.scijava.command.Command;
import org.scijava.command.CommandService;
import org.scijava.module.ModuleService;
import org.scijava.module.ModuleInfo;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.Arrays;

/**
 * SciJava-based capability registry that introspects available tools and commands
 * to provide Gemini with a machine-readable catalog of all available actions.
 */
public class CapabilityRegistry {
    
    private final Context context;
    private final CommandService commandService;
    private final ModuleService moduleService;
    private final PluginService pluginService;
    
    public CapabilityRegistry(Context context) {
        this.context = context;
        this.commandService = context.service(CommandService.class);
        this.moduleService = context.service(ModuleService.class);
        this.pluginService = context.service(PluginService.class);
    }
    
    /**
     * Generate complete capability registry as JSON for Gemini
     */
    public JSONObject generateCapabilityRegistry() {
        JSONObject registry = new JSONObject();
        
        // AutoDense-specific tools
        JSONObject autoDenseTools = introspectAutoDenseTools();
        registry.put("autodense_tools", autoDenseTools);
        
        // ImageJ/SciJava commands
        JSONObject imageJCommands = introspectImageJCommands();
        registry.put("imagej_commands", imageJCommands);
        
        // Analysis pipelines
        JSONObject pipelines = generateAnalysisPipelines();
        registry.put("analysis_pipelines", pipelines);
        
        // Scientific vocabulary
        JSONObject vocabulary = loadScientificVocabulary();
        registry.put("vocabulary", vocabulary);
        
        // Metadata
        registry.put("generated_at", System.currentTimeMillis());
        registry.put("version", "1.0.0");
        registry.put("description", "Complete capability registry for AutoDense laboratory image analysis");
        
        return registry;
    }
    
    /**
     * Introspect GelAnalysisTools to discover available tool functions
     */
    private JSONObject introspectAutoDenseTools() {
        JSONObject tools = new JSONObject();
        
        try {
            Class<?> toolsClass = GelAnalysisTools.class;
            Method[] methods = toolsClass.getDeclaredMethods();
            
            for (Method method : methods) {
                // Only include public methods that return JSONObject and take JSONObject
                if (method.getReturnType() == JSONObject.class && 
                    method.getParameterCount() == 1 &&
                    method.getParameterTypes()[0] == JSONObject.class &&
                    java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
                    
                    String toolName = method.getName();
                    JSONObject toolInfo = new JSONObject();
                    
                    // Extract tool information from method name and javadoc
                    toolInfo.put("name", toolName);
                    toolInfo.put("description", getToolDescription(toolName));
                    toolInfo.put("category", getToolCategory(toolName));
                    toolInfo.put("parameters", getToolParameters(toolName));
                    toolInfo.put("returns", getToolReturns(toolName));
                    toolInfo.put("example_usage", getToolExample(toolName));
                    
                    tools.put(toolName, toolInfo);
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error introspecting AutoDense tools: " + e.getMessage());
        }
        
        return tools;
    }
    
    /**
     * Introspect ImageJ/SciJava commands
     */
    private JSONObject introspectImageJCommands() {
        JSONObject commands = new JSONObject();
        
        try {
            if (commandService != null) {
                // Get all available commands
                List<org.scijava.command.CommandInfo> commandInfos = commandService.getCommandsOfType(Command.class);
                
                for (org.scijava.command.CommandInfo info : commandInfos) {
                    JSONObject commandInfo = new JSONObject();
                    commandInfo.put("title", info.getTitle());
                    commandInfo.put("description", info.getDescription());
                    commandInfo.put("menu_path", info.getMenuPath() != null ? info.getMenuPath().toString() : "");
                    commandInfo.put("category", categorizeImageJCommand(info.getTitle()));
                    
                    // Add parameter information
                    JSONArray params = new JSONArray();
                    info.inputs().forEach(input -> {
                        JSONObject param = new JSONObject();
                        param.put("name", input.getName());
                        param.put("type", input.getType().getSimpleName());
                        param.put("required", input.isRequired());
                        param.put("description", input.getDescription());
                        params.put(param);
                    });
                    commandInfo.put("parameters", params);
                    
                    commands.put(info.getDelegateClassName(), commandInfo);
                }
            }
        } catch (Exception e) {
            System.err.println("Error introspecting ImageJ commands: " + e.getMessage());
        }
        
        return commands;
    }
    
    /**
     * Generate analysis pipeline definitions
     */
    private JSONObject generateAnalysisPipelines() {
        JSONObject pipelines = new JSONObject();
        
        // Gel analysis pipeline
        JSONObject gelPipeline = new JSONObject();
        gelPipeline.put("name", "Complete Gel Analysis");
        gelPipeline.put("description", "Full gel electrophoresis analysis workflow");
        JSONArray gelSteps = new JSONArray();
        gelSteps.put(createPipelineStep("open_image", "Load gel image", true));
        gelSteps.put(createPipelineStep("preprocess", "Enhance image quality", false));
        gelSteps.put(createPipelineStep("detect_lanes", "Find gel lanes", true));
        gelSteps.put(createPipelineStep("detect_bands", "Identify protein/DNA bands", true));
        gelSteps.put(createPipelineStep("quantify_bands", "Measure band intensities", true));
        gelSteps.put(createPipelineStep("calibrate_molecular_weight", "Set MW standards", false));
        gelSteps.put(createPipelineStep("normalize_intensities", "Normalize across lanes", false));
        gelSteps.put(createPipelineStep("export_results", "Save analysis results", true));
        gelPipeline.put("steps", gelSteps);
        pipelines.put("gel_analysis", gelPipeline);
        
        // Colony analysis pipeline
        JSONObject colonyPipeline = new JSONObject();
        colonyPipeline.put("name", "Complete Colony Analysis");
        colonyPipeline.put("description", "Full agar plate colony analysis workflow");
        JSONArray colonySteps = new JSONArray();
        colonySteps.put(createPipelineStep("open_image", "Load plate image", true));
        colonySteps.put(createPipelineStep("preprocess", "Enhance image quality", false));
        colonySteps.put(createPipelineStep("detect_colonies", "Find all colonies", true));
        colonySteps.put(createPipelineStep("count_colonies_by_color", "Count by color groups", false));
        colonySteps.put(createPipelineStep("measure_colony_sizes", "Analyze size distribution", false));
        colonySteps.put(createPipelineStep("check_contamination", "Screen for contamination", false));
        colonySteps.put(createPipelineStep("export_results", "Save analysis results", true));
        colonyPipeline.put("steps", colonySteps);
        pipelines.put("colony_analysis", colonyPipeline);
        
        return pipelines;
    }
    
    private JSONObject createPipelineStep(String tool, String description, boolean required) {
        JSONObject step = new JSONObject();
        step.put("tool", tool);
        step.put("description", description);
        step.put("required", required);
        return step;
    }
    
    /**
     * Load scientific vocabulary from resources
     */
    private JSONObject loadScientificVocabulary() {
        try {
            // This would load from the scientific_vocabulary.json file
            return new JSONObject()
                .put("status", "vocabulary_loaded")
                .put("categories", Arrays.asList(
                    "gel_electrophoresis", "microbiology", "quantification", 
                    "laboratory_techniques", "common_abbreviations"
                ));
        } catch (Exception e) {
            return new JSONObject().put("error", "Failed to load vocabulary");
        }
    }
    
    // Tool metadata methods
    private String getToolDescription(String toolName) {
        Map<String, String> descriptions = new HashMap<>();
        descriptions.put("openImage", "Load laboratory image (gel or agar plate) into analysis session");
        descriptions.put("preprocess", "Apply image enhancements (rotation, contrast, background subtraction)");
        descriptions.put("detectLanes", "Detect lanes in gel electrophoresis images");
        descriptions.put("detectBands", "Identify protein or DNA bands within gel lanes");
        descriptions.put("adjustLanes", "Fine-tune lane positioning and width");
        descriptions.put("quantifyBands", "Measure band intensities with background correction");
        descriptions.put("renderOverlayPng", "Export current analysis view as PNG image");
        descriptions.put("exportResults", "Save analysis results in various formats (CSV, JSON, PNG)");
        descriptions.put("detectColonies", "Detect microbial colonies on agar plates");
        descriptions.put("countColoniesByColor", "Count colonies grouped by color characteristics");
        descriptions.put("measureColonySizes", "Analyze colony size distribution and statistics");
        descriptions.put("checkContamination", "Screen for potential contamination in cultures");
        descriptions.put("calibrateMolecularWeight", "Set up molecular weight calibration using ladder");
        descriptions.put("normalizeIntensities", "Normalize band intensities across lanes");
        descriptions.put("clearSession", "Clear all analysis data and reset session state");
        return descriptions.getOrDefault(toolName, "Tool for laboratory image analysis");
    }
    
    private String getToolCategory(String toolName) {
        if (toolName.contains("Colony") || toolName.contains("Contamination")) return "microbiology";
        if (toolName.contains("Lane") || toolName.contains("Band") || toolName.contains("Molecular")) return "gel_electrophoresis";
        if (toolName.contains("export") || toolName.contains("render")) return "data_export";
        if (toolName.contains("preprocess")) return "image_processing";
        return "general";
    }
    
    private JSONObject getToolParameters(String toolName) {
        JSONObject params = new JSONObject();
        
        // Common parameter: image_handle (required for all tools except openImage)
        if (!toolName.equals("openImage") && !toolName.equals("clearSession")) {
            params.put("image_handle", createParam("string", "Handle to image in session", true));
        }
        
        // Tool-specific parameters
        switch (toolName) {
            case "openImage":
                params.put("path", createParam("string", "File path to image", true));
                break;
            case "preprocess":
                params.put("steps", createParam("array", "Preprocessing steps to apply", true));
                break;
            case "detectLanes":
                params.put("expected_lanes", createParam("integer", "Expected number of lanes", false));
                params.put("constant_spacing", createParam("boolean", "Whether lanes are evenly spaced", false));
                params.put("lane_width_fraction", createParam("number", "Lane width as fraction (0-1)", false));
                params.put("grid_offset", createParam("number", "Horizontal offset adjustment", false));
                break;
            case "detectColonies":
                params.put("min_colony_size", createParam("integer", "Minimum colony size in pixels", false));
                params.put("max_colony_size", createParam("integer", "Maximum colony size in pixels", false));
                params.put("sensitivity", createParam("number", "Detection sensitivity (0-1)", false));
                break;
            case "countColoniesByColor":
                params.put("color_groups", createParam("array", "Colors to count separately", true));
                break;
            case "calibrateMolecularWeight":
                params.put("ladder_type", createParam("string", "Type of MW ladder (protein/dna)", false));
                params.put("ladder_lane", createParam("integer", "Lane containing MW ladder", false));
                break;
        }
        
        return params;
    }
    
    private JSONObject createParam(String type, String description, boolean required) {
        JSONObject param = new JSONObject();
        param.put("type", type);
        param.put("description", description);
        param.put("required", required);
        return param;
    }
    
    private JSONObject getToolReturns(String toolName) {
        JSONObject returns = new JSONObject();
        returns.put("type", "object");
        
        // Common returns
        if (!toolName.equals("clearSession")) {
            returns.put("image_handle", "Handle to processed image");
        }
        
        // Tool-specific returns
        switch (toolName) {
            case "openImage":
                returns.put("width", "Image width in pixels");
                returns.put("height", "Image height in pixels");
                returns.put("title", "Image title/filename");
                break;
            case "detectLanes":
                returns.put("lanes_found", "Number of lanes detected");
                returns.put("overlay_handle", "Handle to lane overlay");
                returns.put("analysis_handle", "Handle to lane analysis data");
                break;
            case "detectColonies":
                returns.put("colonies_found", "Number of colonies detected");
                returns.put("overlay_handle", "Handle to colony overlay");
                returns.put("analysis_handle", "Handle to colony data");
                break;
            case "quantifyBands":
                returns.put("lanes_quantified", "Number of lanes analyzed");
                returns.put("total_bands", "Total number of bands quantified");
                returns.put("results", "Quantification results array");
                break;
        }
        
        return returns;
    }
    
    private String getToolExample(String toolName) {
        Map<String, String> examples = new HashMap<>();
        examples.put("openImage", "{\"path\": \"/path/to/gel.tif\"}");
        examples.put("detectLanes", "{\"image_handle\": \"img_123\", \"expected_lanes\": 12}");
        examples.put("detectColonies", "{\"image_handle\": \"img_456\", \"min_colony_size\": 10}");
        examples.put("countColoniesByColor", "{\"image_handle\": \"img_456\", \"color_groups\": [\"white\", \"pink\", \"red\"]}");
        examples.put("exportResults", "{\"image_handle\": \"img_123\", \"export_formats\": [\"csv\", \"png\"]}");
        return examples.getOrDefault(toolName, "{\"image_handle\": \"img_handle\"}");
    }
    
    private String categorizeImageJCommand(String title) {
        if (title.toLowerCase().contains("filter")) return "image_processing";
        if (title.toLowerCase().contains("measure")) return "quantification";
        if (title.toLowerCase().contains("threshold")) return "segmentation";
        if (title.toLowerCase().contains("analyze")) return "analysis";
        return "general";
    }
    
    /**
     * Generate gel_analysis_intents.json structure from Java registry
     * This replaces the static JSON file with dynamically generated content
     */
    public JSONObject exportGelAnalysisIntents() {
        JSONObject intents = new JSONObject();
        JSONObject intentMap = new JSONObject();
        
        // Generate intents based on tool introspection
        JSONObject tools = introspectAutoDenseTools();
        
        for (String toolName : tools.keySet()) {
            JSONObject tool = tools.getJSONObject(toolName);
            String category = tool.getString("category");
            
            // Create intent based on tool category and name
            JSONObject intent = createIntentFromTool(toolName, tool);
            String intentKey = getIntentKeyFromTool(toolName, category);
            
            intentMap.put(intentKey, intent);
        }
        
        intents.put("intents", intentMap);
        
        // Add parameter extraction patterns
        intents.put("parameter_extraction", createParameterExtractionPatterns());
        
        // Add context awareness
        intents.put("context_awareness", createContextAwareness());
        
        // Add metadata
        intents.put("generated_from", "CapabilityRegistry.java");
        intents.put("generated_at", System.currentTimeMillis());
        intents.put("version", "1.0.0");
        
        return intents;
    }
    
    private JSONObject createIntentFromTool(String toolName, JSONObject tool) {
        JSONObject intent = new JSONObject();
        
        // Generate patterns based on tool name and description
        JSONArray patterns = generatePatternsFromTool(toolName, tool);
        intent.put("patterns", patterns);
        
        // Convert tool parameters to intent parameters
        JSONObject toolParams = tool.getJSONObject("parameters");
        JSONObject intentParams = new JSONObject();
        
        for (String paramName : toolParams.keySet()) {
            JSONObject toolParam = toolParams.getJSONObject(paramName);
            JSONObject intentParam = convertToolParamToIntentParam(paramName, toolParam);
            intentParams.put(paramName, intentParam);
        }
        
        if (intentParams.length() > 0) {
            intent.put("parameters", intentParams);
        }
        
        // Map to tool execution method
        intent.put("action", convertToolNameToAction(toolName));
        
        return intent;
    }
    
    private JSONArray generatePatternsFromTool(String toolName, JSONObject tool) {
        JSONArray patterns = new JSONArray();
        String description = tool.getString("description");
        
        // Generate patterns based on tool name
        String baseName = toolName.toLowerCase().replaceAll("([A-Z])", " $1").trim();
        patterns.put(baseName);
        patterns.put(toolName.toLowerCase());
        
        // Add verb forms from description
        if (description.startsWith("Load")) {
            patterns.put("open").put("load").put("import");
        } else if (description.startsWith("Apply")) {
            patterns.put("enhance").put("preprocess").put("adjust");
        } else if (description.startsWith("Detect") || description.startsWith("Identify")) {
            patterns.put("detect").put("find").put("identify").put("locate");
        } else if (description.startsWith("Measure")) {
            patterns.put("measure").put("quantify").put("analyze");
        } else if (description.startsWith("Export") || description.startsWith("Save")) {
            patterns.put("export").put("save").put("download");
        }
        
        // Add specific patterns for known tools
        switch (toolName) {
            case "detectLanes":
                patterns.put("detect lanes").put("find lanes").put("X lanes").put("lane detection");
                break;
            case "detectBands":
                patterns.put("detect bands").put("find bands").put("protein bands").put("DNA bands");
                break;
            case "detectColonies":
                patterns.put("detect colonies").put("count colonies").put("find colonies");
                break;
            case "calibrateMolecularWeight":
                patterns.put("calibrate").put("molecular weight").put("MW ladder").put("protein ladder");
                break;
            case "normalizeIntensities":
                patterns.put("normalize").put("normalization").put("reference").put("control");
                break;
        }
        
        return patterns;
    }
    
    private JSONObject convertToolParamToIntentParam(String paramName, JSONObject toolParam) {
        JSONObject intentParam = new JSONObject();
        
        String type = toolParam.getString("type");
        JSONArray patterns = new JSONArray();
        
        // Generate patterns based on parameter name and type
        switch (paramName) {
            case "expected_lanes":
            case "lane_count":
                patterns.put("\\d+").put("one").put("two").put("three").put("four")
                       .put("five").put("six").put("seven").put("eight").put("nine")
                       .put("ten").put("eleven").put("twelve");
                JSONObject mapping = new JSONObject();
                mapping.put("one", 1).put("two", 2).put("three", 3).put("four", 4)
                       .put("five", 5).put("six", 6).put("seven", 7).put("eight", 8)
                       .put("nine", 9).put("ten", 10).put("eleven", 11).put("twelve", 12);
                intentParam.put("mapping", mapping);
                break;
            case "sensitivity":
                patterns.put("high sensitivity").put("low sensitivity").put("sensitive")
                       .put("less sensitive").put("\\d*\\.?\\d+");
                break;
            case "ladder_type":
                patterns.put("protein").put("DNA").put("RNA").put("NEB").put("Bio-Rad");
                intentParam.put("default", "protein");
                break;
            case "color_groups":
                patterns.put("white").put("pink").put("red").put("blue").put("green").put("yellow");
                break;
            default:
                if (type.equals("integer")) {
                    patterns.put("\\d+");
                } else if (type.equals("number")) {
                    patterns.put("\\d*\\.?\\d+");
                } else if (type.equals("boolean")) {
                    patterns.put("true").put("false").put("yes").put("no");
                } else {
                    patterns.put(".*"); // Generic string pattern
                }
                break;
        }
        
        intentParam.put("patterns", patterns);
        return intentParam;
    }
    
    private String convertToolNameToAction(String toolName) {
        // Convert camelCase tool names to action method names
        return "execute" + toolName.substring(0, 1).toUpperCase() + toolName.substring(1);
    }
    
    private String getIntentKeyFromTool(String toolName, String category) {
        // Generate intent keys from tool names
        switch (toolName) {
            case "detectLanes": return "lane_detection";
            case "adjustLanes": return "lane_adjustment";
            case "detectBands": return "band_detection";
            case "detectColonies": return "colony_detection";
            case "countColoniesByColor": return "colony_counting";
            case "quantifyBands": return "quantification";
            case "preprocess": return "image_enhancement";
            case "calibrateMolecularWeight": return "calibration";
            case "exportResults": return "export_results";
            case "openImage": return "image_loading";
            default: return toolName.toLowerCase() + "_intent";
        }
    }
    
    private JSONObject createParameterExtractionPatterns() {
        JSONObject extraction = new JSONObject();
        
        JSONObject numbers = new JSONObject();
        JSONArray numberPatterns = new JSONArray();
        numberPatterns.put("\\d+").put("\\d*\\.\\d+");
        numbers.put("patterns", numberPatterns);
        
        JSONObject contextClues = new JSONObject();
        contextClues.put("lanes", new JSONArray().put("lane").put("lanes"));
        contextClues.put("offset", new JSONArray().put("offset").put("shift").put("move"));
        contextClues.put("intensity", new JSONArray().put("intensity").put("brightness").put("contrast"));
        contextClues.put("size", new JSONArray().put("kDa").put("bp").put("kb").put("Da"));
        numbers.put("context_clues", contextClues);
        
        extraction.put("numbers", numbers);
        
        JSONObject ranges = new JSONObject();
        JSONArray rangePatterns = new JSONArray();
        rangePatterns.put("\\d+-\\d+").put("from \\d+ to \\d+").put("between \\d+ and \\d+");
        ranges.put("patterns", rangePatterns);
        extraction.put("ranges", ranges);
        
        return extraction;
    }
    
    private JSONObject createContextAwareness() {
        JSONObject context = new JSONObject();
        
        JSONObject gelState = new JSONObject();
        gelState.put("loaded", "image_loaded");
        gelState.put("lanes_detected", "lanes_available");
        gelState.put("bands_detected", "bands_available");
        gelState.put("calibrated", "calibration_available");
        context.put("gel_state", gelState);
        
        JSONObject suggestedSteps = new JSONObject();
        suggestedSteps.put("after_lane_detection", new JSONArray().put("detect bands").put("calibrate with ladder"));
        suggestedSteps.put("after_band_detection", new JSONArray().put("quantify bands").put("export results"));
        suggestedSteps.put("after_calibration", new JSONArray().put("calculate molecular weights"));
        suggestedSteps.put("after_quantification", new JSONArray().put("normalize data").put("export results"));
        context.put("suggested_next_steps", suggestedSteps);
        
        return context;
    }

    /**
     * Generate capability summary for Gemini system prompt
     */
    public String generateCapabilitySummary() {
        JSONObject registry = generateCapabilityRegistry();
        
        StringBuilder summary = new StringBuilder();
        summary.append("AUTODENSE CAPABILITY REGISTRY:\\n\\n");
        
        // AutoDense tools summary
        JSONObject tools = registry.getJSONObject("autodense_tools");
        summary.append("Available Tools (").append(tools.length()).append("):\\n");
        
        for (String toolName : tools.keySet()) {
            JSONObject tool = tools.getJSONObject(toolName);
            summary.append("• ").append(toolName)
                   .append(" (").append(tool.getString("category")).append("): ")
                   .append(tool.getString("description")).append("\\n");
        }
        
        // Analysis pipelines
        summary.append("\\nAnalysis Pipelines:\\n");
        summary.append("• gel_analysis: Complete gel electrophoresis workflow\\n");
        summary.append("• colony_analysis: Complete agar plate analysis workflow\\n");
        
        // Supported image types
        summary.append("\\nSupported Image Types:\\n");
        summary.append("• Gel electrophoresis (SDS-PAGE, agarose, native gels)\\n");
        summary.append("• Agar plates (bacterial/yeast colonies, various media)\\n");
        
        return summary.toString();
    }
}