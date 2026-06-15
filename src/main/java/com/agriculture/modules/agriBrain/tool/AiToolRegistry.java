package com.agriculture.modules.agriBrain.tool;

import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AiToolRegistry {

    private final Map<String, AiTool> tools = new LinkedHashMap<>();

    public AiToolRegistry(List<AiTool> toolList) {
        for (AiTool tool : toolList) {
            tools.put(tool.getName(), tool);
        }
    }

    public AiTool getTool(String name) {
        return tools.get(name);
    }

    public Collection<AiTool> getAllTools() {
        return tools.values();
    }

    public List<Map<String, Object>> buildToolDefinitions() {
        List<Map<String, Object>> toolDefs = new ArrayList<>();
        for (AiTool tool : tools.values()) {
            Map<String, Object> function = new LinkedHashMap<>();
            function.put("name", tool.getName());
            function.put("description", tool.getDescription());
            function.put("parameters", tool.getParameters());

            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");
            toolDef.put("function", function);
            toolDefs.add(toolDef);
        }
        return toolDefs;
    }
}
