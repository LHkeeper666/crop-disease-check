package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.agriBrain.tool.AiTool;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.inference.mapper.InferenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DetectionTool implements AiTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private InferenceMapper inferenceMapper;

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
                return executeTrend(arguments, companyId);
            } else {
                return executeQuery(arguments, companyId);
            }
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String executeQuery(Map<String, Object> arguments, String companyId) throws JsonProcessingException {
        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();

        // 类型筛选
        String type = (String) arguments.get("type");
        if ("disease".equals(type)) {
            wrapper.isNotNull(Inference::getDiseaseIds);
            wrapper.ne(Inference::getDiseaseIds, "[]");
        } else if ("pest".equals(type)) {
            wrapper.isNotNull(Inference::getPestIds);
            wrapper.ne(Inference::getPestIds, "[]");
        }

        // 日期筛选
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String startDateStr = (String) arguments.get("startDate");
        if (StringUtils.hasText(startDateStr)) {
            LocalDate startDate = LocalDate.parse(startDateStr, dateFormatter);
            wrapper.ge(Inference::getCreatedAt, startDate.atStartOfDay());
        }

        String endDateStr = (String) arguments.get("endDate");
        if (StringUtils.hasText(endDateStr)) {
            LocalDate endDate = LocalDate.parse(endDateStr, dateFormatter);
            wrapper.le(Inference::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }

        // 排序和限制
        wrapper.orderByDesc(Inference::getCreatedAt);
        int limit = 20;
        if (arguments.get("limit") instanceof Number) {
            limit = ((Number) arguments.get("limit")).intValue();
        }
        wrapper.last("LIMIT " + limit);

        List<Inference> inferences = inferenceMapper.selectList(wrapper);

        List<Map<String, Object>> detections = inferences.stream().map(inf -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", inf.getId());
            item.put("diseaseIds", inf.getDiseaseIds());
            item.put("pestIds", inf.getPestIds());
            item.put("detections", inf.getDetections());
            item.put("annotatedImageUrl", inf.getAnnotatedImageUrl());
            item.put("totalElapsedMs", inf.getTotalElapsedMs());
            item.put("createdAt", inf.getCreatedAt() != null ?
                    inf.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", detections.size());
        result.put("detections", detections);

        return objectMapper.writeValueAsString(result);
    }

    private String executeTrend(Map<String, Object> arguments, String companyId) throws JsonProcessingException {
        int days = 7;
        if (arguments.get("days") instanceof Number) {
            days = ((Number) arguments.get("days")).intValue();
        }

        LocalDate startDate = LocalDate.now().minusDays(days);

        LambdaQueryWrapper<Inference> wrapper = new LambdaQueryWrapper<>();
        wrapper.ge(Inference::getCreatedAt, startDate.atStartOfDay());
        wrapper.orderByAsc(Inference::getCreatedAt);

        List<Inference> inferences = inferenceMapper.selectList(wrapper);

        // 按天分组
        Map<LocalDate, List<Inference>> byDay = inferences.stream()
                .collect(Collectors.groupingBy(
                        i -> i.getCreatedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        // 统计全局 Top 病虫害
        Map<String, Long> allDiseaseCount = new LinkedHashMap<>();
        Map<String, Long> allPestCount = new LinkedHashMap<>();

        List<Map<String, Object>> daily = new ArrayList<>();
        for (Map.Entry<LocalDate, List<Inference>> entry : byDay.entrySet()) {
            List<Inference> dayInferences = entry.getValue();
            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", entry.getKey().format(DateTimeFormatter.ofPattern("MM-dd")));
            dayData.put("detections", dayInferences.size());

            // 简化：统计 diseaseIds 和 pestIds 非空的记录数
            long diseaseCount = dayInferences.stream()
                    .filter(i -> StringUtils.hasText(i.getDiseaseIds()) && !"[]".equals(i.getDiseaseIds()))
                    .count();
            long pestCount = dayInferences.stream()
                    .filter(i -> StringUtils.hasText(i.getPestIds()) && !"[]".equals(i.getPestIds()))
                    .count();

            dayData.put("diseaseCount", diseaseCount);
            dayData.put("pestCount", pestCount);
            daily.add(dayData);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", startDate.format(DateTimeFormatter.ofPattern("MM-dd")) + " ~ " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd")));
        result.put("totalDetections", inferences.size());
        result.put("daily", daily);

        return objectMapper.writeValueAsString(result);
    }
}
