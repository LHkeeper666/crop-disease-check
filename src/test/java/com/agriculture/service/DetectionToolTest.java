package com.agriculture.service;

import com.agriculture.modules.agriBrain.tool.impl.DetectionTool;
import com.agriculture.modules.inference.service.InferenceService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectionToolTest {

    @Mock
    private InferenceService inferenceService;

    @InjectMocks
    private DetectionTool detectionTool;

    private Map<String, Object> createDetectionMap(String diseaseName, double confidence) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("class_name", diseaseName);
        map.put("confidence", confidence);
        return map;
    }

    @Nested
    @DisplayName("query action")
    class QueryAction {

        @Test
        @DisplayName("查询最近检测记录")
        void query_default_returnsRecentDetections() {
            Map<String, Object> detection = createDetectionMap("白粉病", 0.85);
            when(inferenceService.listDetections(eq("company-001"), isNull(), isNull(), isNull(), eq(20)))
                    .thenReturn(List.of(detection));

            String result = detectionTool.execute(Map.of("action", "query"), "user-001", "company-001");

            assertTrue(result.contains("\"total\":1"));
            assertTrue(result.contains("白粉病"));
        }

        @Test
        @DisplayName("筛选病害记录")
        void query_diseaseType_returnsDiseaseOnly() {
            Map<String, Object> detection = createDetectionMap("白粉病", 0.85);
            when(inferenceService.listDetections(eq("company-001"), eq("disease"), isNull(), isNull(), eq(20)))
                    .thenReturn(List.of(detection));

            String result = detectionTool.execute(Map.of("action", "query", "type", "disease"), "user-001", "company-001");

            assertTrue(result.contains("\"total\":1"));
        }

        @Test
        @DisplayName("无记录时返回空列表")
        void query_noData_returnsEmptyList() {
            when(inferenceService.listDetections(eq("company-001"), isNull(), isNull(), isNull(), eq(20)))
                    .thenReturn(Collections.emptyList());

            String result = detectionTool.execute(Map.of("action", "query"), "user-001", "company-001");

            assertTrue(result.contains("\"total\":0"));
        }
    }

    @Nested
    @DisplayName("trend action")
    class TrendAction {

        @Test
        @DisplayName("查询最近7天趋势")
        void trend_default_returnsDailyAggregation() {
            Map<String, Object> trendData = new LinkedHashMap<>();
            trendData.put("totalDetections", 2);
            trendData.put("daily", List.of());
            when(inferenceService.getDetectionTrend("company-001", 7)).thenReturn(trendData);

            String result = detectionTool.execute(Map.of("action", "trend"), "user-001", "company-001");

            assertTrue(result.contains("daily"));
            assertTrue(result.contains("\"totalDetections\":2"));
        }

        @Test
        @DisplayName("无数据时返回空趋势")
        void trend_noData_returnsEmptyTrend() {
            Map<String, Object> emptyTrend = new LinkedHashMap<>();
            emptyTrend.put("totalDetections", 0);
            emptyTrend.put("daily", List.of());
            when(inferenceService.getDetectionTrend("company-001", 7)).thenReturn(emptyTrend);

            String result = detectionTool.execute(Map.of("action", "trend"), "user-001", "company-001");

            assertTrue(result.contains("\"totalDetections\":0"));
        }
    }
}
