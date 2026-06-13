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
        assertTrue(result.contains("农业遥测专家"));
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
}
