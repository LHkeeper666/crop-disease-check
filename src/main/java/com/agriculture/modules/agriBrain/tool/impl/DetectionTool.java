package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.agriBrain.tool.AiTool;
import com.agriculture.modules.inference.service.InferenceService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class DetectionTool implements AiTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private InferenceService inferenceService;

    @Override
    public String getName() {
        return "detection";
    }

    @Override
    public String getDescription() {
        return "查询AI检测记录。支持两种操作：\n"
                + "- query: 查询最近的检测记录列表，支持按类型和日期筛选\n"
                + "- trend: 按天聚合检测数量和Top病虫害，用于分析检测趋势";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", List.of("query", "trend"));
        action.put("description", "操作类型：query=查询列表，trend=趋势统计");
        properties.put("action", action);

        Map<String, Object> type = new LinkedHashMap<>();
        type.put("type", "string");
        type.put("enum", List.of("disease", "pest"));
        type.put("description", "筛选类型：disease=仅病害，pest=仅虫害");
        properties.put("type", type);

        Map<String, Object> startDate = new LinkedHashMap<>();
        startDate.put("type", "string");
        startDate.put("description", "开始日期，格式 YYYY-MM-DD");
        properties.put("startDate", startDate);

        Map<String, Object> endDate = new LinkedHashMap<>();
        endDate.put("type", "string");
        endDate.put("description", "结束日期，格式 YYYY-MM-DD");
        properties.put("endDate", endDate);

        Map<String, Object> days = new LinkedHashMap<>();
        days.put("type", "integer");
        days.put("description", "trend 操作的查询天数，默认7");
        properties.put("days", days);

        Map<String, Object> limit = new LinkedHashMap<>();
        limit.put("type", "integer");
        limit.put("description", "返回数量限制，默认20（仅 query 操作有效）");
        properties.put("limit", limit);

        params.put("properties", properties);
        params.put("required", List.of("action"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> arguments, String userId, String companyId) {
        String action = (String) arguments.getOrDefault("action", "query");

        try {
            if ("trend".equals(action)) {
                int days = 7;
                if (arguments.get("days") instanceof Number) {
                    days = ((Number) arguments.get("days")).intValue();
                }
                return objectMapper.writeValueAsString(
                        inferenceService.getDetectionTrend(companyId, days));
            } else {
                String type = (String) arguments.get("type");
                String startDate = (String) arguments.get("startDate");
                String endDate = (String) arguments.get("endDate");
                int limit = 20;
                if (arguments.get("limit") instanceof Number) {
                    limit = ((Number) arguments.get("limit")).intValue();
                }

                Map<String, Object> result = new LinkedHashMap<>();
                List<Map<String, Object>> detections = inferenceService.listDetections(
                        companyId, type, startDate, endDate, limit);
                result.put("total", detections.size());
                result.put("detections", detections);

                return objectMapper.writeValueAsString(result);
            }
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }
}
