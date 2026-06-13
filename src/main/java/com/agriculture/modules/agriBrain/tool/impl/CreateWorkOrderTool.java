package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.agriBrain.tool.AiTool;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
public class CreateWorkOrderTool implements AiTool {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    @Resource
    private WorkOrderMapper workOrderMapper;

    @Override
    public String getName() {
        return "create_work_order";
    }

    @Override
    public String getDescription() {
        return "互动创建工单。采用两步确认流程：\n"
                + "1. 先调用 prepare action 生成工单摘要，展示给用户确认\n"
                + "2. 用户确认后，再调用 create action 真正创建工单\n"
                + "必填字段：title（标题）、type（disease/pest）、severity（LOW/MEDIUM/HIGH/CRITICAL）";
    }

    @Override
    public Map<String, Object> getParameters() {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", "object");

        Map<String, Object> properties = new LinkedHashMap<>();

        Map<String, Object> action = new LinkedHashMap<>();
        action.put("type", "string");
        action.put("enum", List.of("prepare", "create"));
        action.put("description", "操作类型：prepare=准备摘要供确认，create=确认后创建工单");
        properties.put("action", action);

        Map<String, Object> title = new LinkedHashMap<>();
        title.put("type", "string");
        title.put("description", "工单标题");
        properties.put("title", title);

        Map<String, Object> type = new LinkedHashMap<>();
        type.put("type", "string");
        type.put("enum", List.of("disease", "pest"));
        type.put("description", "工单类型：disease=病害, pest=虫害");
        properties.put("type", type);

        Map<String, Object> severity = new LinkedHashMap<>();
        severity.put("type", "string");
        severity.put("enum", List.of("LOW", "MEDIUM", "HIGH", "CRITICAL"));
        severity.put("description", "严重程度");
        properties.put("severity", severity);

        Map<String, Object> gridLabel = new LinkedHashMap<>();
        gridLabel.put("type", "string");
        gridLabel.put("description", "关联网格编号（如 A1）");
        properties.put("gridLabel", gridLabel);

        Map<String, Object> pestName = new LinkedHashMap<>();
        pestName.put("type", "string");
        pestName.put("description", "病虫害名称");
        properties.put("pestName", pestName);

        Map<String, Object> description = new LinkedHashMap<>();
        description.put("type", "string");
        description.put("description", "问题描述");
        properties.put("description", description);

        params.put("properties", properties);
        params.put("required", List.of("action"));
        return params;
    }

    @Override
    public String execute(Map<String, Object> arguments, String userId, String companyId) {
        String action = (String) arguments.getOrDefault("action", "prepare");

        try {
            if ("create".equals(action)) {
                return executeCreate(arguments, userId, companyId);
            } else {
                return executePrepare(arguments);
            }
        } catch (Exception e) {
            return "{\"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String executePrepare(Map<String, Object> arguments) throws JsonProcessingException {
        // 检查必填字段
        List<String> missing = new ArrayList<>();
        String title = (String) arguments.get("title");
        String type = (String) arguments.get("type");
        String severity = (String) arguments.get("severity");

        if (!StringUtils.hasText(title)) missing.add("title");
        if (!StringUtils.hasText(type)) missing.add("type");
        if (!StringUtils.hasText(severity)) missing.add("severity");

        if (!missing.isEmpty()) {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("action", "missing_fields");
            result.put("missing", missing);
            result.put("message", "请向用户追问以下必填信息: " + String.join(", ", missing));
            return objectMapper.writeValueAsString(result);
        }

        // 返回工单摘要
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("action", "confirm");
        summary.put("title", title);
        summary.put("type", type);
        summary.put("severity", severity);
        summary.put("gridLabel", arguments.get("gridLabel"));
        summary.put("pestName", arguments.get("pestName"));
        summary.put("description", arguments.get("description"));
        summary.put("message", "请将以上信息展示给用户确认，用户确认后再调用 create_work_order 的 create action 创建工单");

        return objectMapper.writeValueAsString(summary);
    }

    private String executeCreate(Map<String, Object> arguments, String userId, String companyId) throws JsonProcessingException {
        // 检查必填字段
        String title = (String) arguments.get("title");
        String type = (String) arguments.get("type");
        String severity = (String) arguments.get("severity");

        List<String> missing = new ArrayList<>();
        if (!StringUtils.hasText(title)) missing.add("title");
        if (!StringUtils.hasText(type)) missing.add("type");
        if (!StringUtils.hasText(severity)) missing.add("severity");

        if (!missing.isEmpty()) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", "缺少必填字段: " + String.join(", ", missing));
            return objectMapper.writeValueAsString(error);
        }

        // 创建工单
        WorkOrder workOrder = new WorkOrder();
        workOrder.setTitle(title);
        workOrder.setType(type);
        workOrder.setSeverity(severity);
        workOrder.setStatus("PENDING");
        workOrder.setGridLabel((String) arguments.get("gridLabel"));
        workOrder.setPestName((String) arguments.get("pestName"));
        workOrder.setExpertComment((String) arguments.get("description"));
        workOrder.setCreatedBy(userId);
        workOrder.setCompanyId(companyId);
        workOrder.setCreatedAt(LocalDateTime.now());
        workOrder.setUpdatedAt(LocalDateTime.now());

        workOrderMapper.insert(workOrder);

        // 返回创建结果
        Map<String, Object> workOrderData = new LinkedHashMap<>();
        workOrderData.put("id", workOrder.getId());
        workOrderData.put("title", workOrder.getTitle());
        workOrderData.put("status", workOrder.getStatus());
        workOrderData.put("severity", workOrder.getSeverity());
        workOrderData.put("type", workOrder.getType());
        workOrderData.put("gridLabel", workOrder.getGridLabel());
        workOrderData.put("pestName", workOrder.getPestName());
        workOrderData.put("createdAt", workOrder.getCreatedAt() != null ?
                workOrder.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : null);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("workOrder", workOrderData);

        return objectMapper.writeValueAsString(result);
    }
}
