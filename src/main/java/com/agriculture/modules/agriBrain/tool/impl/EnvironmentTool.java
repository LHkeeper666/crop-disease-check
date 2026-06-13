package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.agriBrain.tool.AiTool;
import com.agriculture.modules.environment.entity.EnvironmentRecord;
import com.agriculture.modules.environment.mapper.EnvironmentMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class EnvironmentTool implements AiTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private EnvironmentMapper environmentMapper;

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

        Map<String, Object> greenhouseId = new LinkedHashMap<>();
        greenhouseId.put("type", "string");
        greenhouseId.put("description", "温室ID，不传则查询所有温室");
        properties.put("greenhouseId", greenhouseId);

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
                return executeTrend(arguments, companyId);
            } else {
                return executeLatest(arguments, companyId);
            }
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String executeLatest(Map<String, Object> arguments, String companyId) throws JsonProcessingException {
        String greenhouseId = (String) arguments.get("greenhouseId");

        if (StringUtils.hasText(greenhouseId)) {
            // 查询指定温室的最新记录
            LambdaQueryWrapper<EnvironmentRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EnvironmentRecord::getGreenhouseId, greenhouseId);
            if (StringUtils.hasText(companyId)) {
                wrapper.eq(EnvironmentRecord::getCompanyId, companyId);
            }
            wrapper.orderByDesc(EnvironmentRecord::getRecordedAt);
            wrapper.last("LIMIT 1");

            EnvironmentRecord record = environmentMapper.selectOne(wrapper);
            if (record == null) {
                return "{\"error\": \"未找到环境数据\"}";
            }

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("greenhouseId", record.getGreenhouseId());
            result.put("airTemp", record.getAirTemp());
            result.put("humidity", record.getHumidity());
            result.put("soilMoisture", record.getSoilMoisture());
            result.put("co2", record.getCo2());
            result.put("lightLevel", record.getLightLevel());
            result.put("soilPh", record.getSoilPh());
            result.put("ec", record.getEc());
            result.put("nitrogen", record.getNitrogen());
            result.put("phosphorus", record.getPhosphorus());
            result.put("potassium", record.getPotassium());
            result.put("recordedAt", record.getRecordedAt() != null ?
                    record.getRecordedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);

            return objectMapper.writeValueAsString(result);
        } else {
            // 查询所有温室的最新记录（每个温室一条）
            LambdaQueryWrapper<EnvironmentRecord> wrapper = new LambdaQueryWrapper<>();
            if (StringUtils.hasText(companyId)) {
                wrapper.eq(EnvironmentRecord::getCompanyId, companyId);
            }
            wrapper.orderByDesc(EnvironmentRecord::getRecordedAt);

            List<EnvironmentRecord> allRecords = environmentMapper.selectList(wrapper);

            // 按 greenhouseId 分组，取每组最新一条
            Map<String, EnvironmentRecord> latestByGreenhouse = allRecords.stream()
                    .collect(Collectors.toMap(
                            EnvironmentRecord::getGreenhouseId,
                            r -> r,
                            (existing, replacement) -> existing.getRecordedAt() != null &&
                                    replacement.getRecordedAt() != null &&
                                    replacement.getRecordedAt().isAfter(existing.getRecordedAt()) ? replacement : existing,
                            LinkedHashMap::new
                    ));

            if (latestByGreenhouse.isEmpty()) {
                return "{\"error\": \"未找到环境数据\"}";
            }

            List<Map<String, Object>> records = latestByGreenhouse.values().stream().map(record -> {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("greenhouseId", record.getGreenhouseId());
                item.put("airTemp", record.getAirTemp());
                item.put("humidity", record.getHumidity());
                item.put("soilMoisture", record.getSoilMoisture());
                item.put("co2", record.getCo2());
                item.put("lightLevel", record.getLightLevel());
                item.put("recordedAt", record.getRecordedAt() != null ?
                        record.getRecordedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
                return item;
            }).collect(Collectors.toList());

            Map<String, Object> result = new LinkedHashMap<>();
            result.put("total", records.size());
            result.put("records", records);

            return objectMapper.writeValueAsString(result);
        }
    }

    private String executeTrend(Map<String, Object> arguments, String companyId) throws JsonProcessingException {
        String greenhouseId = (String) arguments.get("greenhouseId");
        int days = 7;
        if (arguments.get("days") instanceof Number) {
            days = ((Number) arguments.get("days")).intValue();
        }

        LocalDate startDate = LocalDate.now().minusDays(days);

        LambdaQueryWrapper<EnvironmentRecord> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(companyId)) {
            wrapper.eq(EnvironmentRecord::getCompanyId, companyId);
        }
        if (StringUtils.hasText(greenhouseId)) {
            wrapper.eq(EnvironmentRecord::getGreenhouseId, greenhouseId);
        }
        wrapper.ge(EnvironmentRecord::getRecordedAt, startDate.atStartOfDay());
        wrapper.orderByAsc(EnvironmentRecord::getRecordedAt);

        List<EnvironmentRecord> records = environmentMapper.selectList(wrapper);

        // 按天分组
        Map<LocalDate, List<EnvironmentRecord>> byDay = records.stream()
                .collect(Collectors.groupingBy(
                        r -> r.getRecordedAt().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()
                ));

        List<Map<String, Object>> daily = new ArrayList<>();
        for (Map.Entry<LocalDate, List<EnvironmentRecord>> entry : byDay.entrySet()) {
            List<EnvironmentRecord> dayRecords = entry.getValue();
            Map<String, Object> dayData = new LinkedHashMap<>();
            dayData.put("date", entry.getKey().format(DateTimeFormatter.ofPattern("MM-dd")));
            dayData.put("count", dayRecords.size());
            dayData.put("airTemp", aggregateField(dayRecords, EnvironmentRecord::getAirTemp));
            dayData.put("humidity", aggregateField(dayRecords, EnvironmentRecord::getHumidity));
            dayData.put("soilMoisture", aggregateField(dayRecords, EnvironmentRecord::getSoilMoisture));
            dayData.put("co2", aggregateField(dayRecords, EnvironmentRecord::getCo2));
            dayData.put("lightLevel", aggregateField(dayRecords, EnvironmentRecord::getLightLevel));
            daily.add(dayData);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("period", startDate.format(DateTimeFormatter.ofPattern("MM-dd")) + " ~ " +
                LocalDate.now().format(DateTimeFormatter.ofPattern("MM-dd")));
        result.put("days", daily.size());
        result.put("daily", daily);

        return objectMapper.writeValueAsString(result);
    }

    private Map<String, Object> aggregateField(List<EnvironmentRecord> records,
                                                java.util.function.Function<EnvironmentRecord, BigDecimal> getter) {
        List<BigDecimal> values = records.stream()
                .map(getter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        if (values.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("avg", null);
            empty.put("min", null);
            empty.put("max", null);
            return empty;
        }

        BigDecimal sum = values.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal avg = sum.divide(BigDecimal.valueOf(values.size()), 2, RoundingMode.HALF_UP);
        BigDecimal min = values.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO);
        BigDecimal max = values.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO);

        Map<String, Object> agg = new LinkedHashMap<>();
        agg.put("avg", avg);
        agg.put("min", min);
        agg.put("max", max);
        return agg;
    }
}
