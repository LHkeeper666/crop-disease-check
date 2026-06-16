package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.environment.service.EnvironmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EnvironmentToolTest {

    @Mock
    private EnvironmentService environmentService;

    @InjectMocks
    private EnvironmentTool environmentTool;

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
        assertEquals("environment", environmentTool.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(environmentTool.getDescription());
        assertTrue(environmentTool.getDescription().contains("latest"));
        assertTrue(environmentTool.getDescription().contains("trend"));
    }

    @Test
    void testGetParameters() {
        Map<String, Object> params = environmentTool.getParameters();
        assertNotNull(params);
        assertEquals("object", params.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) params.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("action"));
        assertTrue(properties.containsKey("days"));
    }

    @Test
    void testExecuteLatest() throws Exception {
        // 准备测试数据
        List<Map<String, Object>> mockRecords = new ArrayList<>();
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("greenhouseId", "gh001");
        record.put("airTemp", 25.5);
        record.put("humidity", 60.0);
        record.put("recordedAt", "2026-06-15 10:00");
        mockRecords.add(record);

        when(environmentService.getLatestRecords(eq(companyId)))
                .thenReturn(mockRecords);

        // 执行
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "latest");

        String result = environmentTool.execute(arguments, userId, companyId);

        // 验证
        assertNotNull(result);
        assertFalse(result.contains("error"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> resultList = objectMapper.readValue(result, List.class);
        assertEquals(1, resultList.size());
        assertEquals("gh001", resultList.get(0).get("greenhouseId"));

        verify(environmentService).getLatestRecords(companyId);
    }

    @Test
    void testExecuteTrend() throws Exception {
        Map<String, Object> mockTrend = new LinkedHashMap<>();
        mockTrend.put("period", "06-08 ~ 06-15");
        mockTrend.put("days", 7);
        mockTrend.put("daily", new ArrayList<>());

        when(environmentService.getTrendData(eq(companyId), eq(7)))
                .thenReturn(mockTrend);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "trend");

        String result = environmentTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        assertFalse(result.contains("error"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
        assertEquals("06-08 ~ 06-15", resultMap.get("period"));
        assertEquals(7, resultMap.get("days"));

        verify(environmentService).getTrendData(companyId, 7);
    }

    @Test
    void testExecuteTrend_WithCustomDays() throws Exception {
        Map<String, Object> mockTrend = new LinkedHashMap<>();
        when(environmentService.getTrendData(eq(companyId), eq(30)))
                .thenReturn(mockTrend);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "trend");
        arguments.put("days", 30);

        String result = environmentTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        verify(environmentService).getTrendData(companyId, 30);
    }

    @Test
    void testExecute_DefaultAction() throws Exception {
        // 默认 action 是 latest
        List<Map<String, Object>> mockRecords = new ArrayList<>();
        when(environmentService.getLatestRecords(eq(companyId)))
                .thenReturn(mockRecords);

        Map<String, Object> arguments = new LinkedHashMap<>();
        // 不设置 action，测试默认值

        String result = environmentTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        verify(environmentService).getLatestRecords(companyId);
    }

    @Test
    void testExecute_EmptyCompanyId() throws Exception {
        List<Map<String, Object>> mockRecords = new ArrayList<>();
        when(environmentService.getLatestRecords(eq("")))
                .thenReturn(mockRecords);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "latest");

        String result = environmentTool.execute(arguments, userId, "");

        assertNotNull(result);
        verify(environmentService).getLatestRecords("");
    }

    @Test
    void testExecute_ServiceException() throws Exception {
        when(environmentService.getLatestRecords(anyString()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "latest");

        String result = environmentTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        assertTrue(result.contains("error"));
        assertTrue(result.contains("数据库连接失败"));
    }
}
