package com.agriculture.service;

import com.agriculture.modules.agriBrain.tool.impl.EnvironmentTool;
import com.agriculture.modules.environment.entity.EnvironmentRecord;
import com.agriculture.modules.environment.mapper.EnvironmentMapper;
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
class EnvironmentToolTest {

    @Mock
    private EnvironmentMapper environmentMapper;

    @InjectMocks
    private EnvironmentTool environmentTool;

    private EnvironmentRecord createRecord(String greenhouseId, BigDecimal temp, BigDecimal humidity, LocalDateTime recordedAt) {
        EnvironmentRecord record = new EnvironmentRecord();
        record.setId("env-" + System.nanoTime());
        record.setGreenhouseId(greenhouseId);
        record.setCompanyId("company-001");
        record.setAirTemp(temp);
        record.setHumidity(humidity);
        record.setCo2(new BigDecimal("450"));
        record.setLightLevel(new BigDecimal("12000"));
        record.setSoilPh(new BigDecimal("6.5"));
        record.setRecordedAt(recordedAt);
        return record;
    }

    @Nested
    @DisplayName("latest action")
    class LatestAction {

        @Test
        @DisplayName("查询指定温室最新环境数据")
        void latest_withGreenhouseId_returnsLatestRecord() {
            EnvironmentRecord record = createRecord("GH-A1", new BigDecimal("28.5"), new BigDecimal("82.0"),
                    LocalDateTime.of(2026, 6, 13, 10, 0, 0));
            when(environmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(record);

            String result = environmentTool.execute(Map.of("action", "latest", "greenhouseId", "GH-A1"), "user-001", "company-001");

            assertTrue(result.contains("28.5"));
            assertTrue(result.contains("82.0"));
            assertTrue(result.contains("GH-A1"));
        }

        @Test
        @DisplayName("查询所有温室最新数据")
        void latest_noGreenhouseId_returnsAllLatest() {
            EnvironmentRecord r1 = createRecord("GH-A1", new BigDecimal("28.5"), new BigDecimal("82.0"),
                    LocalDateTime.of(2026, 6, 13, 10, 0, 0));
            EnvironmentRecord r2 = createRecord("GH-A2", new BigDecimal("25.0"), new BigDecimal("70.0"),
                    LocalDateTime.of(2026, 6, 13, 10, 0, 0));
            when(environmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r1, r2));

            String result = environmentTool.execute(Map.of("action", "latest"), "user-001", "company-001");

            assertTrue(result.contains("\"total\":2"));
            assertTrue(result.contains("GH-A1"));
            assertTrue(result.contains("GH-A2"));
        }

        @Test
        @DisplayName("无数据时返回错误")
        void latest_noData_returnsError() {
            when(environmentMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            String result = environmentTool.execute(Map.of("action", "latest", "greenhouseId", "GH-X"), "user-001", "company-001");

            assertTrue(result.contains("error"));
        }
    }

    @Nested
    @DisplayName("trend action")
    class TrendAction {

        @Test
        @DisplayName("查询最近7天趋势")
        void trend_default_returnsDailyAggregation() {
            EnvironmentRecord r1 = createRecord("GH-A1", new BigDecimal("28.0"), new BigDecimal("80.0"),
                    LocalDateTime.now().minusDays(1));
            EnvironmentRecord r2 = createRecord("GH-A1", new BigDecimal("30.0"), new BigDecimal("85.0"),
                    LocalDateTime.now());
            when(environmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(List.of(r1, r2));

            String result = environmentTool.execute(Map.of("action", "trend", "greenhouseId", "GH-A1"), "user-001", "company-001");

            assertTrue(result.contains("daily"));
            assertTrue(result.contains("period"));
        }

        @Test
        @DisplayName("无数据时返回空趋势")
        void trend_noData_returnsEmptyDaily() {
            when(environmentMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Collections.emptyList());

            String result = environmentTool.execute(Map.of("action", "trend"), "user-001", "company-001");

            assertTrue(result.contains("\"days\":0"));
        }
    }

    @Test
    @DisplayName("未知action返回错误")
    void unknownAction_returnsError() {
        String result = environmentTool.execute(Map.of("action", "unknown"), "user-001", "company-001");
        // 会走 default latest 逻辑，但无数据返回 error
        assertTrue(result.contains("error") || result.contains("未找到"));
    }
}
