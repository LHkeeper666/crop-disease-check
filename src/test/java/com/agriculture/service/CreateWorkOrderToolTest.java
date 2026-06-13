package com.agriculture.service;

import com.agriculture.modules.agriBrain.tool.impl.CreateWorkOrderTool;
import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class CreateWorkOrderToolTest {

    @Mock
    private WorkOrderMapper workOrderMapper;

    @InjectMocks
    private CreateWorkOrderTool createWorkOrderTool;

    @Nested
    @DisplayName("prepare action")
    class PrepareAction {

        @Test
        @DisplayName("完整信息返回确认摘要")
        void prepare_allFieldsPresent_returnsConfirmSummary() {
            Map<String, Object> args = Map.of(
                    "action", "prepare",
                    "title", "温室A1白粉病",
                    "type", "disease",
                    "severity", "HIGH",
                    "pestName", "白粉病",
                    "gridLabel", "A1"
            );

            String result = createWorkOrderTool.execute(args, "user-001", "company-001");

            assertTrue(result.contains("\"action\":\"confirm\""));
            assertTrue(result.contains("温室A1白粉病"));
            assertTrue(result.contains("HIGH"));
        }

        @Test
        @DisplayName("缺少必填字段返回 missing_fields")
        void prepare_missingFields_returnsMissingFields() {
            Map<String, Object> args = Map.of(
                    "action", "prepare",
                    "title", "温室A1白粉病"
                    // 缺少 type 和 severity
            );

            String result = createWorkOrderTool.execute(args, "user-001", "company-001");

            assertTrue(result.contains("missing_fields"));
            assertTrue(result.contains("type"));
            assertTrue(result.contains("severity"));
        }

        @Test
        @DisplayName("完全无字段时返回所有缺失")
        void prepare_noFields_returnsAllMissing() {
            Map<String, Object> args = Map.of("action", "prepare");

            String result = createWorkOrderTool.execute(args, "user-001", "company-001");

            assertTrue(result.contains("missing_fields"));
            assertTrue(result.contains("title"));
        }
    }

    @Nested
    @DisplayName("create action")
    class CreateAction {

        @Test
        @DisplayName("确认后创建工单成功")
        void create_allFieldsPresent_createsWorkOrder() {
            doReturn(1).when(workOrderMapper).insert(isA(WorkOrder.class));

            Map<String, Object> args = Map.of(
                    "action", "create",
                    "title", "温室A1白粉病",
                    "type", "disease",
                    "severity", "HIGH",
                    "pestName", "白粉病",
                    "gridLabel", "A1"
            );

            String result = createWorkOrderTool.execute(args, "user-001", "company-001");

            assertTrue(result.contains("\"success\":true"));
            assertTrue(result.contains("workOrder"));
            assertTrue(result.contains("PENDING"));
            verify(workOrderMapper).insert(isA(WorkOrder.class));
        }

        @Test
        @DisplayName("缺少必填字段时创建失败")
        void create_missingFields_returnsError() {
            Map<String, Object> args = Map.of(
                    "action", "create",
                    "title", "温室A1白粉病"
                    // 缺少 type 和 severity
            );

            String result = createWorkOrderTool.execute(args, "user-001", "company-001");

            assertTrue(result.contains("error"));
            assertTrue(result.contains("缺少必填字段"));
            verify(workOrderMapper, never()).insert(isA(WorkOrder.class));
        }

        @Test
        @DisplayName("创建时自动填充 createdBy 和 companyId")
        void create_autoFillsCreatedByAndCompanyId() {
            doReturn(1).when(workOrderMapper).insert(isA(WorkOrder.class));

            Map<String, Object> args = Map.of(
                    "action", "create",
                    "title", "测试工单",
                    "type", "pest",
                    "severity", "LOW"
            );

            createWorkOrderTool.execute(args, "user-001", "company-001");

            org.mockito.ArgumentCaptor<WorkOrder> captor = org.mockito.ArgumentCaptor.forClass(WorkOrder.class);
            verify(workOrderMapper).insert(captor.capture());
            WorkOrder captured = captor.getValue();
            assertEquals("user-001", captured.getCreatedBy());
            assertEquals("company-001", captured.getCompanyId());
            assertEquals("PENDING", captured.getStatus());
        }
    }
}
