package com.agriculture.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("DailyReport 实体新字段测试")
class DailyReportEntityTest {

    @Test
    @DisplayName("detections 字段 getter/setter")
    void detectionsField() {
        DailyReport entity = new DailyReport();
        entity.setDetections(50);
        assertEquals(50, entity.getDetections());

        entity.setDetections(null);
        assertNull(entity.getDetections());
    }

    @Test
    @DisplayName("diseaseCount 字段 getter/setter")
    void diseaseCountField() {
        DailyReport entity = new DailyReport();
        entity.setDiseaseCount(30);
        assertEquals(30, entity.getDiseaseCount());
    }

    @Test
    @DisplayName("pestCount 字段 getter/setter")
    void pestCountField() {
        DailyReport entity = new DailyReport();
        entity.setPestCount(20);
        assertEquals(20, entity.getPestCount());
    }

    @Test
    @DisplayName("handledRate 字段 getter/setter")
    void handledRateField() {
        DailyReport entity = new DailyReport();
        entity.setHandledRate(new BigDecimal("0.75"));
        assertEquals(0, new BigDecimal("0.75").compareTo(entity.getHandledRate()));
    }

    @Test
    @DisplayName("greenhouseId 字段 getter/setter")
    void greenhouseIdField() {
        DailyReport entity = new DailyReport();
        entity.setGreenhouseId("gh-001");
        assertEquals("gh-001", entity.getGreenhouseId());
    }

    @Test
    @DisplayName("companyId 字段 getter/setter")
    void companyIdField() {
        DailyReport entity = new DailyReport();
        entity.setCompanyId("comp-001");
        assertEquals("comp-001", entity.getCompanyId());
    }

    @Test
    @DisplayName("所有新字段可同时设置")
    void allNewFieldsTogether() {
        DailyReport entity = new DailyReport();
        entity.setDetections(100);
        entity.setDiseaseCount(60);
        entity.setPestCount(40);
        entity.setHandledRate(new BigDecimal("0.88"));
        entity.setGreenhouseId("gh-002");
        entity.setCompanyId("comp-002");

        assertEquals(100, entity.getDetections());
        assertEquals(60, entity.getDiseaseCount());
        assertEquals(40, entity.getPestCount());
        assertEquals(0, new BigDecimal("0.88").compareTo(entity.getHandledRate()));
        assertEquals("gh-002", entity.getGreenhouseId());
        assertEquals("comp-002", entity.getCompanyId());
    }

    @Test
    @DisplayName("原有字段不受影响")
    void existingFieldsStillWork() {
        DailyReport entity = new DailyReport();
        entity.setId("dr-001");
        entity.setReportDate(LocalDate.of(2026, 6, 9));
        entity.setSummaryJson("{\"detections\":12}");
        entity.setHtmlContent("<html>report</html>");
        entity.setEmailSent((byte) 1);
        entity.setEmailSentAt(LocalDateTime.of(2026, 6, 9, 8, 0, 0));
        entity.setCreatedAt(LocalDateTime.of(2026, 6, 9, 7, 0, 0));

        assertEquals("dr-001", entity.getId());
        assertEquals(LocalDate.of(2026, 6, 9), entity.getReportDate());
        assertEquals("{\"detections\":12}", entity.getSummaryJson());
        assertEquals("<html>report</html>", entity.getHtmlContent());
        assertEquals((byte) 1, entity.getEmailSent());
    }
}
