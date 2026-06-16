package com.agriculture.service;

import com.agriculture.modules.agriBrain.tool.impl.EnvironmentTool;
import com.agriculture.modules.environment.service.EnvironmentService;
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
class EnvironmentToolTest {

    @Mock
    private EnvironmentService environmentService;

    @InjectMocks
    private EnvironmentTool environmentTool;

    private Map<String, Object> createRecordMap(String greenhouseId, String temp, String humidity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("greenhouseId", greenhouseId);
        map.put("airTemp", temp);
        map.put("humidity", humidity);
        map.put("co2", "450");
        map.put("lightLevel", "12000");
        map.put("soilPh", "6.5");
        return map;
    }

    @Nested
    @DisplayName("latest action")
    class LatestAction {

        @Test
        @DisplayName("查询指定温室最新环境数据")
        void latest_withGreenhouseId_returnsLatestRecord() {
            Map<String, Object> record = createRecordMap("GH-A1", "28.5", "82.0");
            when(environmentService.getLatestRecords("company-001")).thenReturn(List.of(record));

            String result = environmentTool.execute(Map.of("action", "latest", "greenhouseId", "GH-A1"), "user-001", "company-001");

            assertTrue(result.contains("28.5"));
            assertTrue(result.contains("82.0"));
            assertTrue(result.contains("GH-A1"));
        }

        @Test
        @DisplayName("查询所有温室最新数据")
        void latest_noGreenhouseId_returnsAllLatest() {
            Map<String, Object> r1 = createRecordMap("GH-A1", "28.5", "82.0");
            Map<String, Object> r2 = createRecordMap("GH-A2", "25.0", "70.0");
            when(environmentService.getLatestRecords("company-001")).thenReturn(List.of(r1, r2));

            String result = environmentTool.execute(Map.of("action", "latest"), "user-001", "company-001");

            // 返回的是 JSON 数组 [{...}, {...}]，不是包装对象
            assertTrue(result.contains("GH-A1"));
            assertTrue(result.contains("GH-A2"));
            assertTrue(result.contains("28.5"));
        }

        @Test
        @DisplayName("无数据时返回空数组")
        void latest_noData_returnsEmptyArray() {
            when(environmentService.getLatestRecords("company-001")).thenReturn(Collections.emptyList());

            String result = environmentTool.execute(Map.of("action", "latest", "greenhouseId", "GH-X"), "user-001", "company-001");

            // 空列表序列化为 []
            assertEquals("[]", result);
        }
    }

    @Nested
    @DisplayName("trend action")
    class TrendAction {

        @Test
        @DisplayName("查询最近7天趋势")
        void trend_default_returnsDailyAggregation() {
            Map<String, Object> trendData = new LinkedHashMap<>();
            trendData.put("period", "7d");
            trendData.put("daily", List.of());
            when(environmentService.getTrendData("company-001", 7)).thenReturn(trendData);

            String result = environmentTool.execute(Map.of("action", "trend", "greenhouseId", "GH-A1"), "user-001", "company-001");

            assertTrue(result.contains("daily"));
            assertTrue(result.contains("period"));
        }

        @Test
        @DisplayName("无数据时返回空趋势")
        void trend_noData_returnsEmptyDaily() {
            Map<String, Object> emptyTrend = new LinkedHashMap<>();
            emptyTrend.put("days", 0);
            emptyTrend.put("daily", List.of());
            when(environmentService.getTrendData("company-001", 7)).thenReturn(emptyTrend);

            String result = environmentTool.execute(Map.of("action", "trend"), "user-001", "company-001");

            assertTrue(result.contains("\"days\":0"));
        }
    }

    @Test
    @DisplayName("未知action走latest逻辑")
    void unknownAction_returnsLatestData() {
        Map<String, Object> record = createRecordMap("GH-A1", "28.5", "82.0");
        when(environmentService.getLatestRecords("company-001")).thenReturn(List.of(record));

        String result = environmentTool.execute(Map.of("action", "unknown"), "user-001", "company-001");

        // 会走 default latest 逻辑，返回序列化的列表
        assertTrue(result.contains("GH-A1"));
    }
}
