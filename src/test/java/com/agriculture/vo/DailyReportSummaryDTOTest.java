package com.agriculture.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DailyReportSummaryDTO 新字段测试")
class DailyReportSummaryDTOTest {

    @Test
    @DisplayName("greenhouseId 字段 getter/setter")
    void greenhouseIdField() {
        DailyReportSummaryDTO dto = new DailyReportSummaryDTO();
        dto.setGreenhouseId("gh-001");
        assertEquals("gh-001", dto.getGreenhouseId());
    }

    @Test
    @DisplayName("所有字段可同时设置")
    void allFieldsTogether() {
        DailyReportSummaryDTO dto = new DailyReportSummaryDTO();
        dto.setGreenhouseId("gh-002");
        dto.setTotalInspections(50);
        dto.setTotalDetections(12);
        dto.setDiseaseCount(8);
        dto.setPestCount(4);
        dto.setWorkorderHandledRate(new BigDecimal("0.75"));

        DailyReportSummaryDTO.TopGridDTO grid = new DailyReportSummaryDTO.TopGridDTO();
        grid.setGridLabel("A1");
        grid.setCount(5);
        dto.setTopGrids(List.of(grid));

        DailyReportSummaryDTO.TopPestDTO pest = new DailyReportSummaryDTO.TopPestDTO();
        pest.setName("番茄晚疫病");
        pest.setCount(6);
        dto.setTopPests(List.of(pest));

        assertEquals("gh-002", dto.getGreenhouseId());
        assertEquals(50, dto.getTotalInspections());
        assertEquals(12, dto.getTotalDetections());
        assertEquals(8, dto.getDiseaseCount());
        assertEquals(4, dto.getPestCount());
        assertEquals(0, new BigDecimal("0.75").compareTo(dto.getWorkorderHandledRate()));
        assertEquals(1, dto.getTopGrids().size());
        assertEquals("A1", dto.getTopGrids().get(0).getGridLabel());
        assertEquals(1, dto.getTopPests().size());
        assertEquals("番茄晚疫病", dto.getTopPests().get(0).getName());
    }
}
