package com.agriculture.entity;

import com.agriculture.modules.workorder.entity.WorkOrder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WorkOrder 实体新字段测试")
class WorkOrderEntityTest {

    @Test
    @DisplayName("type 字段 getter/setter")
    void typeField() {
        WorkOrder entity = new WorkOrder();
        entity.setType("disease");
        assertEquals("disease", entity.getType());

        entity.setType("pest");
        assertEquals("pest", entity.getType());

        entity.setType(null);
        assertNull(entity.getType());
    }

    @Test
    @DisplayName("gridLabel 字段 getter/setter")
    void gridLabelField() {
        WorkOrder entity = new WorkOrder();
        entity.setGridLabel("A1");
        assertEquals("A1", entity.getGridLabel());
    }

    @Test
    @DisplayName("pestName 字段 getter/setter")
    void pestNameField() {
        WorkOrder entity = new WorkOrder();
        entity.setPestName("番茄晚疫病");
        assertEquals("番茄晚疫病", entity.getPestName());
    }

    @Test
    @DisplayName("confidence 字段 getter/setter")
    void confidenceField() {
        WorkOrder entity = new WorkOrder();
        entity.setConfidence(new BigDecimal("0.92"));
        assertEquals(0, new BigDecimal("0.92").compareTo(entity.getConfidence()));
    }

    @Test
    @DisplayName("createdBy 字段 getter/setter")
    void createdByField() {
        WorkOrder entity = new WorkOrder();
        entity.setCreatedBy("user-001");
        assertEquals("user-001", entity.getCreatedBy());
    }

    @Test
    @DisplayName("companyId 字段 getter/setter")
    void companyIdField() {
        WorkOrder entity = new WorkOrder();
        entity.setCompanyId("comp-001");
        assertEquals("comp-001", entity.getCompanyId());
    }

    @Test
    @DisplayName("所有新字段可同时设置")
    void allNewFieldsTogether() {
        WorkOrder entity = new WorkOrder();
        entity.setType("disease");
        entity.setGridLabel("B3");
        entity.setPestName("蚜虫");
        entity.setConfidence(new BigDecimal("0.85"));
        entity.setCreatedBy("user-002");
        entity.setCompanyId("comp-002");

        assertEquals("disease", entity.getType());
        assertEquals("B3", entity.getGridLabel());
        assertEquals("蚜虫", entity.getPestName());
        assertEquals(0, new BigDecimal("0.85").compareTo(entity.getConfidence()));
        assertEquals("user-002", entity.getCreatedBy());
        assertEquals("comp-002", entity.getCompanyId());
    }

    @Test
    @DisplayName("原有字段不受影响")
    void existingFieldsStillWork() {
        WorkOrder entity = new WorkOrder();
        entity.setId("wo-001");
        entity.setTitle("测试工单");
        entity.setSeverity("HIGH");
        entity.setStatus("PENDING");
        entity.setInferenceId("inf-001");
        entity.setAssignedTo("user-001");
        entity.setCreatedAt(LocalDateTime.of(2026, 6, 9, 10, 0, 0));
        entity.setUpdatedAt(LocalDateTime.of(2026, 6, 9, 10, 0, 0));

        assertEquals("wo-001", entity.getId());
        assertEquals("测试工单", entity.getTitle());
        assertEquals("HIGH", entity.getSeverity());
        assertEquals("PENDING", entity.getStatus());
        assertEquals("inf-001", entity.getInferenceId());
        assertEquals("user-001", entity.getAssignedTo());
    }
}
