package com.agriculture.service;

import com.agriculture.common.exception.GlobalExceptionHandler;
import com.agriculture.modules.statistics.controller.StatisticController;
import com.agriculture.modules.statistics.service.StatisticService;
import com.agriculture.modules.statistics.vo.StatisticsOverviewVO;
import com.agriculture.modules.statistics.vo.TrendStatisticsVO;
import com.agriculture.modules.grid.vo.GridStatisticsVO;
import com.agriculture.modules.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StatisticController 单元测试")
class StatisticControllerTest {

    private MockMvc mockMvc;

    @Mock private StatisticService statisticService;
    @Mock private SysUserMapper sysUserMapper;
    @InjectMocks private StatisticController statisticController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(statisticController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Nested
    @DisplayName("GET /statistics/overview - 概览统计")
    class Overview {

        @Test
        @DisplayName("返回概览数据")
        void overview_returnsData() throws Exception {
            StatisticsOverviewVO vo = new StatisticsOverviewVO();
            when(statisticService.getOverview(eq(7), anyString())).thenReturn(vo);

            mockMvc.perform(get("/statistics/overview").param("days", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("GET /statistics/trend - 趋势统计")
    class Trend {

        @Test
        @DisplayName("返回趋势数据")
        void trend_returnsData() throws Exception {
            when(statisticService.getTrend(eq(7), eq("DAY"), anyString())).thenReturn(List.of());

            mockMvc.perform(get("/statistics/trend").param("days", "7").param("granularity", "DAY"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }

    @Nested
    @DisplayName("GET /statistics/grid - 网格统计")
    class GridStats {

        @Test
        @DisplayName("返回网格统计数据")
        void gridStats_returnsList() throws Exception {
            when(statisticService.getGridStatistics(eq(7), anyString())).thenReturn(List.of());

            mockMvc.perform(get("/statistics/grid").param("days", "7"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200));
        }
    }
}
