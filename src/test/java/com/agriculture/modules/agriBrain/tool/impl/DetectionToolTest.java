package com.agriculture.modules.agriBrain.tool.impl;

import com.agriculture.modules.inference.service.InferenceService;
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
class DetectionToolTest {

    @Mock
    private InferenceService inferenceService;

    @InjectMocks
    private DetectionTool detectionTool;

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
        assertEquals("detection", detectionTool.getName());
    }

    @Test
    void testGetDescription() {
        assertNotNull(detectionTool.getDescription());
        assertTrue(detectionTool.getDescription().contains("query"));
        assertTrue(detectionTool.getDescription().contains("trend"));
    }

    @Test
    void testGetParameters() {
        Map<String, Object> params = detectionTool.getParameters();
        assertNotNull(params);
        assertEquals("object", params.get("type"));

        @SuppressWarnings("unchecked")
        Map<String, Object> properties = (Map<String, Object>) params.get("properties");
        assertNotNull(properties);
        assertTrue(properties.containsKey("action"));
        assertTrue(properties.containsKey("type"));
        assertTrue(properties.containsKey("startDate"));
        assertTrue(properties.containsKey("endDate"));
        assertTrue(properties.containsKey("days"));
        assertTrue(properties.containsKey("limit"));
    }

    @Test
    void testExecuteQuery_WithCompanyId() throws Exception {
        // 准备测试数据
        List<Map<String, Object>> mockDetections = new ArrayList<>();
        Map<String, Object> detection = new LinkedHashMap<>();
        detection.put("id", "inf001");
        detection.put("diseaseIds", "[30]");
        detection.put("createdAt", "2026-06-15 10:00");
        mockDetections.add(detection);

        when(inferenceService.listDetections(eq(companyId), isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(mockDetections);

        // 执行
        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "query");

        String result = detectionTool.execute(arguments, userId, companyId);

        // 验证
        assertNotNull(result);
        assertFalse(result.contains("error"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
        assertEquals(1, resultMap.get("total"));

        verify(inferenceService).listDetections(companyId, null, null, null, 20);
    }

    @Test
    void testExecuteQuery_WithTypeFilter() throws Exception {
        List<Map<String, Object>> mockDetections = new ArrayList<>();
        when(inferenceService.listDetections(eq(companyId), eq("disease"), isNull(), isNull(), eq(20)))
                .thenReturn(mockDetections);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "query");
        arguments.put("type", "disease");

        String result = detectionTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        verify(inferenceService).listDetections(companyId, "disease", null, null, 20);
    }

    @Test
    void testExecuteQuery_WithDateRange() throws Exception {
        List<Map<String, Object>> mockDetections = new ArrayList<>();
        when(inferenceService.listDetections(eq(companyId), isNull(), eq("2026-06-01"), eq("2026-06-15"), eq(10)))
                .thenReturn(mockDetections);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "query");
        arguments.put("startDate", "2026-06-01");
        arguments.put("endDate", "2026-06-15");
        arguments.put("limit", 10);

        String result = detectionTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        verify(inferenceService).listDetections(companyId, null, "2026-06-01", "2026-06-15", 10);
    }

    @Test
    void testExecuteTrend() throws Exception {
        Map<String, Object> mockTrend = new LinkedHashMap<>();
        mockTrend.put("period", "06-08 ~ 06-15");
        mockTrend.put("totalDetections", 50);
        mockTrend.put("daily", new ArrayList<>());

        when(inferenceService.getDetectionTrend(eq(companyId), eq(7)))
                .thenReturn(mockTrend);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "trend");

        String result = detectionTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        assertFalse(result.contains("error"));

        @SuppressWarnings("unchecked")
        Map<String, Object> resultMap = objectMapper.readValue(result, Map.class);
        assertEquals("06-08 ~ 06-15", resultMap.get("period"));
        assertEquals(50, resultMap.get("totalDetections"));

        verify(inferenceService).getDetectionTrend(companyId, 7);
    }

    @Test
    void testExecuteTrend_WithCustomDays() throws Exception {
        Map<String, Object> mockTrend = new LinkedHashMap<>();
        when(inferenceService.getDetectionTrend(eq(companyId), eq(30)))
                .thenReturn(mockTrend);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "trend");
        arguments.put("days", 30);

        String result = detectionTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        verify(inferenceService).getDetectionTrend(companyId, 30);
    }

    @Test
    void testExecute_EmptyCompanyId() throws Exception {
        List<Map<String, Object>> mockDetections = new ArrayList<>();
        when(inferenceService.listDetections(eq(""), isNull(), isNull(), isNull(), eq(20)))
                .thenReturn(mockDetections);

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "query");

        String result = detectionTool.execute(arguments, userId, "");

        assertNotNull(result);
        verify(inferenceService).listDetections("", null, null, null, 20);
    }

    @Test
    void testExecute_ServiceException() throws Exception {
        when(inferenceService.listDetections(anyString(), any(), any(), any(), anyInt()))
                .thenThrow(new RuntimeException("数据库连接失败"));

        Map<String, Object> arguments = new LinkedHashMap<>();
        arguments.put("action", "query");

        String result = detectionTool.execute(arguments, userId, companyId);

        assertNotNull(result);
        assertTrue(result.contains("error"));
        assertTrue(result.contains("数据库连接失败"));
    }
}
