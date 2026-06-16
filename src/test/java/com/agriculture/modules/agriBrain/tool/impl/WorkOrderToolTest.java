package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.workorder.entity.WorkOrder;
import com.agriculture.modules.workorder.mapper.WorkOrderMapper;
import com.agriculture.modules.workorder.service.WorkOrderService;
import com.agriculture.modules.workorder.vo.WorkOrderVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkOrderToolTest {

    @Mock
    private WorkOrderService workOrderService;

    @Mock
    private WorkOrderMapper workOrderMapper;

    @InjectMocks
    private WorkOrderTool workOrderTool;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private String userId;
    private String companyId;

    @BeforeEach
    void setUp() {
        userId = "user001";
        companyId = "comp001";
    }

    @Test
    void testGetName() {
        assertEquals("work_order", workOrderTool.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(workOrderTool.getDescription());
        assertTrue(workOrderTool.getDescription().contains("query"));
        assertTrue(workOrderTool.getDescription().contains("stats"));
    }

    @Test
    void testGetParameters() {
        Map<String, Object> params = workOrderTool.getParameters();
        assertNotNull(params);
        assertEquals("object", params.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) params.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("action"));
        assertTrue(properties.containsKey("status"));
        assertTrue(properties.containsKey("severity"));
        assertTrue(properties.containsKey("type"));
        assertTrue(properties.containsKey("startDate"));
        assertTrue(properties.containsKey("endDate"));
        assertTrue(properties.containsKey("limit"));
    }

    @Test
    void testExecuteQuery() throws Exception {
        // 准备测试数据
        Page<WorkOrderVO> mockPage = new Page<>(1, 20, 1);
        List<WorkOrderVO> records = new ArrayList<>();
        WorkOrderVO vo = new WorkOrderVO();
        vo.setId(1L);
        vo.setTitle("测试工单");
        vo.setStatus("PENDING");
        vo.setSeverity("HIGH");
        vo.setType("disease");
        vo.setGridLabel("A1");
        vo.setPestName("番茄晚疫病");
        vo.setConfidence(new BigDecimal("0.95"));
        vo.setCreatedAt(LocalDateTime.of(2026, 6, 15, 10, 0));
        records.add(vo);
        mockPage.setRecords(records);

        when(workOrderService.listWorkOrders(isNull(), isNull(), isNull(), isNull(), eq(1), eq(20), eq(companyId)))
                .thenReturn(mockPage);

        // 执行
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "query");

        String result = workOrderTool.execute(arguments, userId, companyId);

        // 验证
        assertNotNull(result);
        assertFalse(result.contains("error"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
        assertEquals(1, resultMap.get("total"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orders = (List<Map<String, Object>>) resultMap.get("orders");
        assertEquals(1, orders.size());
        assertEquals("测试工单", orders.get(0).get("title"));
        assertEquals("PENDING", orders.get(0).get("status"));

        verify(workOrderService).listWorkOrders(null, null, null, null, 1, 20, companyId);
    }

    @Test
    void testExecuteQuery_WithFilters() throws Exception {
        Page<WorkOrderVO> mockPage = new Page<>(1, 10, 0);
        mockPage.setRecords(new ArrayList<>());

        when(workOrderService.listWorkOrders(eq("PENDING"), eq("HIGH"), isNull(), isNull(), eq(1), eq(10), eq(companyId)))
                .thenReturn(mockPage);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "query");
        arguments.put("status", "PENDING");
        arguments.put("severity", "HIGH");
        arguments.put("limit", 10);

        String result = workOrderTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        verify(workOrderService).listWorkOrders("PENDING", "HIGH", null, null, 1, 10, companyId);
    }

    @Test
    void testExecuteStats() throws Exception {
        // 准备测试数据
        List<WorkOrder> mockOrders = new ArrayList<>();
        WorkOrder order1 = new WorkOrder();
        order1.setStatus("PENDING");
        order1.setSeverity("HIGH");
        order1.setType("disease");
        mockOrders.add(order1);

        WorkOrder order2 = new WorkOrder();
        order2.setStatus("DONE");
        order2.setSeverity("LOW");
        order2.setType("pest");
        mockOrders.add(order2);

        when(workOrderMapper.selectList(any()))
                .thenReturn(mockOrders);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "stats");

        String result = workOrderTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        assertFalse(result.contains("error"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
        assertEquals(2, resultMap.get("total"));

        @SuppressWarnings("unchecked")
        Map<String, Object> byStatus = (Map<String, Object>) resultMap.get("byStatus");
        assertNotNull(byStatus);
        assertEquals(1, byStatus.get("PENDING"));
        assertEquals(1, byStatus.get("DONE"));

        @SuppressWarnings("unchecked")
        Map<String, Object> bySeverity = (Map<String, Object>) resultMap.get("bySeverity");
        assertNotNull(bySeverity);
        assertEquals(1, bySeverity.get("HIGH"));
        assertEquals(1, bySeverity.get("LOW"));
    }

    @Test
    void testExecuteQuery_ServiceException() throws Exception {
        when(workOrderService.listWorkOrders(any(), any(), any(), any(), anyInt(), anyInt(), anyString()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "query");

        String result = workOrderTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        assertTrue(result.contains("error"));
        assertTrue(result.contains("数据库连接失败"));
    }

    @Test
    void testExecute_DefaultAction() throws Exception {
        // 默认 action 是 query
        Page<WorkOrderVO> mockPage = new Page<>(1, 20, 0);
        mockPage.setRecords(new ArrayList<>());

        when(workOrderService.listWorkOrders(isNull(), isNull(), isNull(), isNull(), eq(1), eq(20), eq(companyId)))
                .thenReturn(mockPage);

        Map<String, Object> arguments = new LinkedHashMap<>();
        // 不设置 action，测试默认值

        String result = workOrderTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        verify(workOrderService).listWorkOrders(null, null, null, null, 1, 20, companyId);
    }
}
