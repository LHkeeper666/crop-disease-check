package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.agriBrain.tool.AiTool;
import com.agriculture.modules.environment.service.EnvironmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class EnvironmentTool implements AiTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private EnvironmentService environmentService;

    @Override
    public String getName() {
        return "environment";
    }

    @Override
    public String getDescription() {
        return "查询环境传感器数据。支持两种操作：\n"
                + "- latest: 查询指定温室或所有温室的最新环境数据（温度、湿度、CO2、光照、pH、EC、N/P/K）\n"
                + "- trend: 查询最近N天的环境数据趋势，按天聚合返回均值/最大/最小值";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", List.of("latest", "trend"));
        action.put("description", "操作类型：latest=查询最新值，trend=查询趋势");
        properties.put("action", action);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "trend 操作的查询天数，默认7");
        properties.put("days", days);

        params.put("properties", properties);
        params.put("required", List.of("action"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> arguments, String userId, String companyId) {
        String action = (String) arguments.getOrDefault("action", "latest");

        try {
            if ("trend".equals(action)) {
                int days = 7;
                if (arguments.get("days") instanceof Number) {
                    days = ((Number) arguments.get("days")).intValue();
                }
                return objectMapper.writeValueAsString(
                        environmentService.getTrendData(companyId, days));
            } else {
                return objectMapper.writeValueAsString(
                        environmentService.getLatestRecords(companyId));
            }
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
