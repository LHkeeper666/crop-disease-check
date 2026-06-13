package com.agriculture.service;

import com.agriculture.modules.agriBrain.tool.impl.DetectionTool;
import com.agriculture.modules.inference.entity.Inference;
import com.agriculture.modules.inference.mapper.InferenceMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DetectionToolTest {

    @Mock
    private InferenceMapper inferenceMapper;

    @InjectMocks
    private DetectionTool detectionTool;

    private Inference createInference(String diseaseIds, String pestIds, LocalDateTime createdAt) {
        Inference inf = new Inference();
        inf.setId("inf-" + System.nanoTime());
        inf.setDiseaseIds(diseaseIds);
        inf.setPestIds(pestIds);
        inf.setDetections("[{\"class_name\":\"白粉病\",\"confidence\":0.85}]");
        inf.setTotalElapsedMs(new BigDecimal("120"));
        inf.setCreatedAt(createdAt);
        return inf;
    }

    @Nested
    @DisplayName("query action")
    class QueryAction {

        @Test
        @DisplayName("查询最近检测记录")
        void query_default_returnsRecentDetections() {
            Inference inf = createInference("[0,3]", "[22]", LocalDateTime.now());
            when(inferenceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(inf));

            String result = detectionTool.execute(Map.of("action", "query"), "user-001", "company-001");

            assertTrue(result.contains("\"total\":1"));
            assertTrue(result.contains("白粉病"));
        }

        @Test
        @DisplayName("筛选病害记录")
        void query_diseaseType_returnsDiseaseOnly() {
            Inference inf = createInference("[0]", null, LocalDateTime.now());
            when(inferenceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(inf));

            String result = detectionTool.execute(Map.of("action", "query", "type", "disease"), "user-001", "company-001");

            assertTrue(result.contains("\"total\":1"));
        }

        @Test
        @DisplayName("无记录时返回空列表")
        void query_noData_returnsEmptyList() {
            when(inferenceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

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
            Inference inf1 = createInference("[0]", "[22]", LocalDateTime.now().minusDays(1));
            Inference inf2 = createInference("[3]", null, LocalDateTime.now());
            when(inferenceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(inf1, inf2));

            String result = detectionTool.execute(Map.of("action", "trend"), "user-001", "company-001");

            assertTrue(result.contains("daily"));
            assertTrue(result.contains("\"totalDetections\":2"));
        }

        @Test
        @DisplayName("无数据时返回空趋势")
        void trend_noData_returnsEmptyTrend() {
            when(inferenceMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            String result = detectionTool.execute(Map.of("action", "trend"), "user-001", "company-001");

            assertTrue(result.contains("\"totalDetections\":0"));
        }
    }
}
