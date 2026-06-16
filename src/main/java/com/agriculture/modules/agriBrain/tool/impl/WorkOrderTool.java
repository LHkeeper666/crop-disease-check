package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.agriBrain.tool.AiTool;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.agriculture.modules.workorder.service.WorkOrderService;
import com.agriculture.modules.workorder.vo.WorkOrderVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
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
public class WorkOrderTool implements AiTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private WorkOrderService workOrderService;

    @Resource
    private WorkOrderMapper workOrderMapper;

    @Override
    public String getName() {
        return "work_order";
    }

    @Override
    public String getDescription() {
        return "查询工单数据。支持两种操作：\n"
                + "- query: 查询工单列表，支持按状态、严重程度、类型、日期筛选\n"
                + "- stats: 获取工单统计信息，包括各状态/严重程度/类型的数量分布";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", List.of("query", "stats"));
        action.put("description", "操作类型：query=查询列表，stats=获取统计");
        properties.put("action", action);

        Map<String, Object> status = new LinkedHashMap<>();
        status.put("type", "string");
        status.put("enum", List.of("PENDING", "PROCESSING", "DONE", "IGNORED", "ESCALATED"));
        status.put("description", "工单状态筛选（仅 query 操作有效）");
        properties.put("status", status);

        Map<String, Object> severity = new LinkedHashMap<>();
        severity.put("type", "string");
        severity.put("enum", List.of("LOW", "MEDIUM", "HIGH", "CRITICAL"));
        severity.put("description", "严重程度筛选（仅 query 操作有效）");
        properties.put("severity", severity);

        Map<String, Object> type = new LinkedHashMap<>();
        type.put("type", "string");
        type.put("enum", List.of("disease", "pest"));
        type.put("description", "工单类型：disease=病害, pest=虫害（仅 query 操作有效）");
        properties.put("type", type);

        Map<String, Object> startDate = new LinkedHashMap<>();
        startDate.put("type", "string");
        startDate.put("description", "开始日期，格式 YYYY-MM-DD");
        properties.put("startDate", startDate);

        Map<String, Object> endDate = new LinkedHashMap<>();
        endDate.put("type", "string");
        endDate.put("description", "结束日期，格式 YYYY-MM-DD");
        properties.put("endDate", endDate);

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
            if ("stats".equals(action)) {
                return executeStats(arguments, companyId);
            } else {
                return executeQuery(arguments, companyId);
            }
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String executeQuery(Map<String, Object> arguments, String companyId) throws Exception {
        // 解析参数
        String status = (String) arguments.get("status");
        String severity = (String) arguments.get("severity");
        String type = (String) arguments.get("type");

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        LocalDateTime startDate = null;
        LocalDateTime endDate = null;

        String startDateStr = (String) arguments.get("startDate");
        if (StringUtils.hasText(startDateStr)) {
            startDate = LocalDate.parse(startDateStr, dateFormatter).atStartOfDay();
        }

        String endDateStr = (String) arguments.get("endDate");
        if (StringUtils.hasText(endDateStr)) {
            endDate = LocalDate.parse(endDateStr, dateFormatter).atTime(LocalTime.MAX);
        }

        int limit = 20;
        if (arguments.get("limit") instanceof Number) {
            limit = ((Number) arguments.get("limit")).intValue();
        }

        // 调用 Service（已包含企业隔离）
        IPage<WorkOrderVO> page = workOrderService.listWorkOrders(
                status, severity, startDate, endDate, 1, limit, companyId);

        // 转换为 Tool 返回格式
        List<Map<String, Object>> orderList = page.getRecords().stream().map(vo -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", vo.getId());
            item.put("title", vo.getTitle());
            item.put("status", vo.getStatus());
            item.put("severity", vo.getSeverity());
            item.put("type", vo.getType());
            item.put("grid", vo.getGridLabel());
            item.put("pest", vo.getPestName());
            item.put("confidence", vo.getConfidence());
            item.put("createdAt", vo.getCreatedAt() != null ?
                    vo.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);
            return item;
        }).collect(Collectors.toList());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", page.getTotal());
        result.put("orders", orderList);

        return objectMapper.writeValueAsString(result);
    }

    private String executeStats(Map<String, Object> arguments, String companyId) throws Exception {
        LambdaQueryWrapper<WorkOrder> wrapper = new LambdaQueryWrapper<>();

        // 企业隔离
        if (StringUtils.hasText(companyId)) {
            wrapper.eq(WorkOrder::getCompanyId, companyId);
        }

        // 日期筛选
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String startDateStr = (String) arguments.get("startDate");
        if (StringUtils.hasText(startDateStr)) {
            LocalDate startDate = LocalDate.parse(startDateStr, dateFormatter);
            wrapper.ge(WorkOrder::getCreatedAt, startDate.atStartOfDay());
        }

        String endDateStr = (String) arguments.get("endDate");
        if (StringUtils.hasText(endDateStr)) {
            LocalDate endDate = LocalDate.parse(endDateStr, dateFormatter);
            wrapper.le(WorkOrder::getCreatedAt, endDate.atTime(LocalTime.MAX));
        }

        List<WorkOrder> orders = workOrderMapper.selectList(wrapper);

        // 按状态统计
        Map<String, Long> byStatus = orders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getStatus, Collectors.counting()));

        // 按严重程度统计
        Map<String, Long> bySeverity = orders.stream()
                .collect(Collectors.groupingBy(WorkOrder::getSeverity, Collectors.counting()));

        // 按类型统计
        Map<String, Long> byType = orders.stream()
                .collect(Collectors.groupingBy(wo -> wo.getType() != null ? wo.getType() : "unknown",
                        Collectors.counting()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("total", orders.size());
        result.put("byStatus", byStatus);
        result.put("bySeverity", bySeverity);
        result.put("byType", byType);

        return objectMapper.writeValueAsString(result);
    }
}
