package com.agriculture.controller;

import com.agriculture.dto.EnvironmentReportDTO;
import com.agriculture.entity.EnvironmentRecord;
import com.agriculture.exception.BusinessException;
import com.agriculture.exception.GlobalExceptionHandler;
import com.agriculture.service.EnvironmentService;
import com.agriculture.vo.EnvironmentCurrentVO;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EnvironmentController 单元测试")
class EnvironmentControllerTest {

    private MockMvc mockMvc;

    @Mock
    private EnvironmentService environmentService;

    @InjectMocks
    private EnvironmentController environmentController;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule());

    private EnvironmentCurrentVO mockCurrentVO;
    private EnvironmentRecord mockRecord;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(environmentController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(new LocalValidatorFactoryBean())
                .build();

        // 构造当前数据 VO
        mockCurrentVO = new EnvironmentCurrentVO();
        mockCurrentVO.setGreenhouseId("gh-001");
        mockCurrentVO.setSectorId("GH-A1");
        mockCurrentVO.setRecordedAt(LocalDateTime.of(2026, 6, 10, 10, 30, 0));

        EnvironmentCurrentVO.EnvironmentData env = new EnvironmentCurrentVO.EnvironmentData();
        env.setAirTemp(new EnvironmentCurrentVO.MetricValue(
                new BigDecimal("23.6"), "°C", "normal",
                new BigDecimal("15"), new BigDecimal("35")));
        env.setSoilMoisture(new EnvironmentCurrentVO.MetricValue(
                new BigDecimal("65.2"), "%", "normal",
                new BigDecimal("40"), new BigDecimal("80")));
        env.setHumidity(new EnvironmentCurrentVO.MetricValue(
                new BigDecimal("78.5"), "%", "warning",
                new BigDecimal("40"), new BigDecimal("75")));
        env.setLightLevel(new EnvironmentCurrentVO.MetricValue(
                new BigDecimal("1230"), "lux", "normal",
                new BigDecimal("200"), new BigDecimal("50000")));
        mockCurrentVO.setEnvironment(env);

        EnvironmentCurrentVO.GrowthMetrics growth = new EnvironmentCurrentVO.GrowthMetrics();
        growth.setCo2(new EnvironmentCurrentVO.MetricValue(
                new BigDecimal("420"), "ppm", "normal", null, null));
        mockCurrentVO.setGrowthMetrics(growth);

        EnvironmentCurrentVO.EnergyData energy = new EnvironmentCurrentVO.EnergyData();
        energy.setCurrent(new BigDecimal("55.44"));
        energy.setMax(new BigDecimal("100.0"));
        energy.setUnit("Kw");
        energy.setTrend("stable");
        mockCurrentVO.setEnergy(energy);

        // 构造历史记录
        mockRecord = new EnvironmentRecord();
        mockRecord.setId("env-001");
        mockRecord.setGreenhouseId("gh-001");
        mockRecord.setAirTemp(new BigDecimal("23.6"));
        mockRecord.setRecordedAt(LocalDateTime.of(2026, 6, 10, 10, 30, 0));
    }

    // ==================== 当前数据接口 ====================

    @Nested
    @DisplayName("当前环境数据接口")
    class GetCurrent {

        @Test
        @DisplayName("正常查询返回完整数据")
        void currentSuccess() throws Exception {
            when(environmentService.getCurrentData("gh-001")).thenReturn(mockCurrentVO);

            mockMvc.perform(get("/environment/current").param("greenhouseId", "gh-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.greenhouseId").value("gh-001"))
                    .andExpect(jsonPath("$.data.sectorId").value("GH-A1"))
                    .andExpect(jsonPath("$.data.environment.airTemp.value").value(23.6))
                    .andExpect(jsonPath("$.data.environment.airTemp.status").value("normal"))
                    .andExpect(jsonPath("$.data.environment.humidity.status").value("warning"))
                    .andExpect(jsonPath("$.data.energy.current").value(55.44));
        }

        @Test
        @DisplayName("无数据返回40100")
        void noData_returns40100() throws Exception {
            when(environmentService.getCurrentData("gh-001"))
                    .thenThrow(new BusinessException(40100, "该温室暂无环境数据"));

            mockMvc.perform(get("/environment/current").param("greenhouseId", "gh-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40100))
                    .andExpect(jsonPath("$.message").value("该温室暂无环境数据"));
        }
    }

    // ==================== 历史数据接口 ====================

    @Nested
    @DisplayName("历史环境数据接口")
    class GetHistory {

        @Test
        @DisplayName("默认参数查询")
        void historyDefault() throws Exception {
            Page<EnvironmentRecord> page = new Page<>(1, 100, 1);
            page.setRecords(List.of(mockRecord));
            when(environmentService.getHistoryData(isNull(), isNull(), isNull(), isNull(), eq(1), eq(100)))
                    .thenReturn(page);

            mockMvc.perform(get("/environment/history"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].id").value("env-001"))
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("带分页参数查询")
        void historyWithPagination() throws Exception {
            Page<EnvironmentRecord> page = new Page<>(2, 50, 0);
            page.setRecords(List.of());
            when(environmentService.getHistoryData(isNull(), isNull(), isNull(), isNull(), eq(2), eq(50)))
                    .thenReturn(page);

            mockMvc.perform(get("/environment/history")
                            .param("page", "2")
                            .param("size", "50"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isEmpty());
        }
    }

    // ==================== 数据上报接口 ====================

    @Nested
    @DisplayName("环境数据上报接口")
    class Report {

        @Test
        @DisplayName("成功上报")
        void reportSuccess() throws Exception {
            EnvironmentReportDTO dto = new EnvironmentReportDTO();
            dto.setGreenhouseId("gh-001");
            dto.setAirTemp(new BigDecimal("25.0"));

            when(environmentService.reportData(any(EnvironmentReportDTO.class))).thenReturn("env-new-001");

            mockMvc.perform(post("/environment/report")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").value("env-new-001"))
                    .andExpect(jsonPath("$.message").value("环境数据上报成功"));
        }

        @Test
        @DisplayName("greenhouseId为空返回400")
        void reportEmptyGreenhouseId_returns400() throws Exception {
            EnvironmentReportDTO dto = new EnvironmentReportDTO();
            dto.setGreenhouseId("");

            mockMvc.perform(post("/environment/report")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value(400));
        }

        @Test
        @DisplayName("无效greenhouseId返回40100")
        void reportInvalidGreenhouseId_returns40100() throws Exception {
            EnvironmentReportDTO dto = new EnvironmentReportDTO();
            dto.setGreenhouseId("not-exist");

            when(environmentService.reportData(any(EnvironmentReportDTO.class)))
                    .thenThrow(new BusinessException(40100, "greenhouseId 为空或不存在"));

            mockMvc.perform(post("/environment/report")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40100))
                    .andExpect(jsonPath("$.message").value("greenhouseId 为空或不存在"));
        }

        @Test
        @DisplayName("上报频率过高返回40102")
        void reportTooFrequent_returns40102() throws Exception {
            EnvironmentReportDTO dto = new EnvironmentReportDTO();
            dto.setGreenhouseId("gh-001");
            dto.setAirTemp(new BigDecimal("25.0"));

            when(environmentService.reportData(any(EnvironmentReportDTO.class)))
                    .thenThrow(new BusinessException(40102, "数据上报频率过高，请勿超过每分钟1次"));

            mockMvc.perform(post("/environment/report")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(40102))
                    .andExpect(jsonPath("$.message").value("数据上报频率过高，请勿超过每分钟1次"));
        }
    }
}
