package com.agriculture.service;

import com.agriculture.common.service.impl.StgTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class StgTemplateServiceTest {

    private StgTemplateService templateService;

    @BeforeEach
    void setUp() {
        templateService = new StgTemplateService();
        templateService.init();
    }

    @Test
    void testRenderSystemPrompt() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("date", "2026-06-13");
        attributes.put("dayOfWeek", "周五");

        String result = templateService.render("system_prompt", attributes);

        assertNotNull(result);
        assertTrue(result.contains("2026-06-13"));
        assertTrue(result.contains("周五"));
        assertTrue(result.contains("农作物疾病检测系统"));
    }

    @Test
    void testRenderQuickAdvice() {
        String result = templateService.render("quick_advice", null);

        assertNotNull(result);
        assertTrue(result.contains("农业管理建议报告"));
    }

    @Test
    void testRenderNonExistentTemplate() {
        assertThrows(IllegalArgumentException.class, () -> {
            templateService.render("non_existent", null);
        });
    }

    @Test
    void testRenderEmailPrompt() {
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("title", "番茄晚疫病工单");
        attributes.put("severity", "HIGH");
        attributes.put("gridLabel", "A1");
        attributes.put("pestName", "番茄晚疫病");
        attributes.put("confidence", "92%");
        attributes.put("status", "PENDING");
        attributes.put("createdAt", "2026-06-15T10:00:00");

        String result = templateService.render("email_prompt", attributes);

        assertNotNull(result);
        assertTrue(result.contains("番茄晚疫病工单"));
        assertTrue(result.contains("HIGH"));
        assertTrue(result.contains("A1"));
        assertTrue(result.contains("92%"));
        assertTrue(result.contains("PENDING"));
    }
}
