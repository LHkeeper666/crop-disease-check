package com.agriculture.template;

import org.antlr.runtime.RecognitionException;
import org.stringtemplate.v4.ST;
import org.stringtemplate.v4.STGroup;
import org.stringtemplate.v4.STGroupFile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TemplateSyntaxTest {

    @Test
    void testSystemPromptTemplateSyntax() {
        STGroup group = new STGroupFile("templates/agri-brain/system_prompt.stg");
        ST template = group.getInstanceOf("system_prompt");
        assertNotNull(template, "system_prompt template should exist");
    }

    @Test
    void testQuickAdviceTemplateSyntax() {
        STGroup group = new STGroupFile("templates/agri-brain/quick_advice.stg");
        ST template = group.getInstanceOf("quick_advice");
        assertNotNull(template, "quick_advice template should exist");
    }

    @Test
    void testSystemPromptRendering() {
        STGroup group = new STGroupFile("templates/agri-brain/system_prompt.stg");
        ST template = group.getInstanceOf("system_prompt");
        template.add("date", "2026-06-13");
        template.add("dayOfWeek", "周五");

        String result = template.render();
        assertTrue(result.contains("2026-06-13"), "Template should contain date");
        assertTrue(result.contains("周五"), "Template should contain day of week");
    }
}
