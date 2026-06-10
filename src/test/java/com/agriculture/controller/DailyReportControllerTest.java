package com.agriculture.controller;

import com.agriculture.exception.GlobalExceptionHandler;
import com.agriculture.service.DailyReportService;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.agriculture.vo.DailyReportDetailVO;
import com.agriculture.vo.DailyReportSummaryDTO;
import com.agriculture.vo.DailyReportVO;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class DailyReportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private DailyReportService dailyReportService;

    @InjectMocks
    private DailyReportController dailyReportController;

    private DailyReportVO mockReportVO;
    private DailyReportDetailVO mockDetailVO;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @BeforeEach
    void setUp() {
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setObjectMapper(objectMapper);

        mockMvc = MockMvcBuilders.standaloneSetup(dailyReportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(converter)
                .build();

        DailyReportSummaryDTO summary = new DailyReportSummaryDTO();
        summary.setGreenhouseId("gh-001");
        summary.setTotalInspections(50);
        summary.setTotalDetections(12);
        summary.setDiseaseCount(8);
        summary.setPestCount(4);
        summary.setWorkorderHandledRate(new BigDecimal("0.75"));
        summary.setTopGrids(List.of(
                createTopGrid("A1", 5),
                createTopGrid("B3", 3)
        ));
        summary.setTopPests(List.of(
                createTopPest("番茄晚疫病", 6),
                createTopPest("蚜虫", 4)
        ));

        mockReportVO = new DailyReportVO();
        mockReportVO.setId("dr-001");
        mockReportVO.setReportDate(LocalDate.of(2026, 6, 9));
        mockReportVO.setSummary(summary);
        mockReportVO.setEmailSent((byte) 1);
        mockReportVO.setEmailSentAt(LocalDateTime.of(2026, 6, 9, 8, 0, 0));
        mockReportVO.setCreatedAt(LocalDateTime.of(2026, 6, 9, 7, 0, 0));

        mockDetailVO = new DailyReportDetailVO();
        mockDetailVO.setId("dr-001");
        mockDetailVO.setReportDate(LocalDate.of(2026, 6, 9));
        mockDetailVO.setSummaryJson(Map.of("detections", 12, "diseaseCount", 8));
        mockDetailVO.setHtmlContent("<html><body>日报内容</body></html>");
        mockDetailVO.setEmailSent((byte) 1);
        mockDetailVO.setEmailSentAt(LocalDateTime.of(2026, 6, 9, 8, 0, 0));
        mockDetailVO.setCreatedAt(LocalDateTime.of(2026, 6, 9, 7, 0, 0));
    }

    private DailyReportSummaryDTO.TopGridDTO createTopGrid(String gridLabel, int count) {
        DailyReportSummaryDTO.TopGridDTO dto = new DailyReportSummaryDTO.TopGridDTO();
        dto.setGridLabel(gridLabel);
        dto.setCount(count);
        return dto;
    }

    private DailyReportSummaryDTO.TopPestDTO createTopPest(String name, int count) {
        DailyReportSummaryDTO.TopPestDTO dto = new DailyReportSummaryDTO.TopPestDTO();
        dto.setName(name);
        dto.setCount(count);
        return dto;
    }

    // ==================== 日报列表接口 ====================

    @Nested
    @DisplayName("日报列表接口")
    class ListReports {

        @Test
        @DisplayName("无条件查询日报列表")
        void listReports_noParams_returnsPage() throws Exception {
            Page<DailyReportVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockReportVO));
            when(dailyReportService.listReports(isNull(), isNull(), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/daily-report/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].id").value("dr-001"))
                    .andExpect(jsonPath("$.data.records[0].reportDate").value("2026-06-09"))
                    .andExpect(jsonPath("$.data.total").value(1));
        }

        @Test
        @DisplayName("日报列表返回 summary 中的 greenhouseId 字段")
        void listReports_returnsGreenhouseIdInSummary() throws Exception {
            Page<DailyReportVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockReportVO));
            when(dailyReportService.listReports(isNull(), isNull(), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/daily-report/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records[0].summary.greenhouseId").value("gh-001"))
                    .andExpect(jsonPath("$.data.records[0].summary.totalInspections").value(50))
                    .andExpect(jsonPath("$.data.records[0].summary.totalDetections").value(12))
                    .andExpect(jsonPath("$.data.records[0].summary.diseaseCount").value(8))
                    .andExpect(jsonPath("$.data.records[0].summary.pestCount").value(4))
                    .andExpect(jsonPath("$.data.records[0].summary.workorderHandledRate").value(0.75));
        }

        @Test
        @DisplayName("日报列表 summary 中的 topGrids 和 topPests")
        void listReports_returnsTopGridsAndTopPests() throws Exception {
            Page<DailyReportVO> page = new Page<>(1, 20, 1);
            page.setRecords(List.of(mockReportVO));
            when(dailyReportService.listReports(isNull(), isNull(), eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/daily-report/list"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.records[0].summary.topGrids[0].gridLabel").value("A1"))
                    .andExpect(jsonPath("$.data.records[0].summary.topGrids[0].count").value(5))
                    .andExpect(jsonPath("$.data.records[0].summary.topPests[0].name").value("番茄晚疫病"))
                    .andExpect(jsonPath("$.data.records[0].summary.topPests[0].count").value(6));
        }

        @Test
        @DisplayName("按日期范围筛选")
        void listReports_filterByDateRange() throws Exception {
            Page<DailyReportVO> page = new Page<>(1, 20, 0);
            page.setRecords(List.of());
            when(dailyReportService.listReports(
                    eq(LocalDate.of(2026, 6, 1)),
                    eq(LocalDate.of(2026, 6, 9)),
                    eq(1), eq(20)))
                    .thenReturn(page);

            mockMvc.perform(get("/daily-report/list")
                            .param("startDate", "2026-06-01")
                            .param("endDate", "2026-06-09"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.records").isEmpty());
        }

        @Test
        @DisplayName("分页查询")
        void listReports_withPagination() throws Exception {
            Page<DailyReportVO> page = new Page<>(2, 10, 25);
            page.setRecords(List.of(mockReportVO));
            when(dailyReportService.listReports(isNull(), isNull(), eq(2), eq(10)))
                    .thenReturn(page);

            mockMvc.perform(get("/daily-report/list")
                            .param("page", "2")
                            .param("size", "10"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.current").value(2))
                    .andExpect(jsonPath("$.data.size").value(10))
                    .andExpect(jsonPath("$.data.total").value(25));
        }
    }

    // ==================== 日报详情接口 ====================

    @Nested
    @DisplayName("日报详情接口")
    class GetReportDetail {

        @Test
        @DisplayName("查询存在的日报")
        void getDetail_existingId_returnsDetail() throws Exception {
            when(dailyReportService.getReportDetail("dr-001")).thenReturn(mockDetailVO);

            mockMvc.perform(get("/daily-report/dr-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.id").value("dr-001"))
                    .andExpect(jsonPath("$.data.reportDate").value("2026-06-09"))
                    .andExpect(jsonPath("$.data.htmlContent").value("<html><body>日报内容</body></html>"))
                    .andExpect(jsonPath("$.data.summaryJson.detections").value(12))
                    .andExpect(jsonPath("$.data.summaryJson.diseaseCount").value(8));
        }

        @Test
        @DisplayName("查询不存在的日报返回错误")
        void getDetail_nonExistingId_returnsError() throws Exception {
            when(dailyReportService.getReportDetail("not-exist"))
                    .thenThrow(new com.agriculture.exception.BusinessException(404, "日报不存在"));

            mockMvc.perform(get("/daily-report/not-exist"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(404))
                    .andExpect(jsonPath("$.message").value("日报不存在"));
        }
    }
}
