package com.agriculture.entity;

import com.agriculture.modules.workorder.vo.EmailPreviewVO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("EmailPreviewVO 实体测试")
class EmailPreviewVOTest {

    @Test
    @DisplayName("getter/setter 正常工作")
    void getterSetter_worksCorrectly() {
        EmailPreviewVO vo = new EmailPreviewVO();
        vo.setToUserId("u-001");
        vo.setToName("刘专家");
        vo.setToEmail("expert@test.com");
        vo.setSubject("工单通知");
        vo.setContent("邮件正文内容");

        assertEquals("u-001", vo.getToUserId());
        assertEquals("刘专家", vo.getToName());
        assertEquals("expert@test.com", vo.getToEmail());
        assertEquals("工单通知", vo.getSubject());
        assertEquals("邮件正文内容", vo.getContent());
    }

    @Test
    @DisplayName("字段可为 null")
    void fields_canBeNull() {
        EmailPreviewVO vo = new EmailPreviewVO();

        assertNull(vo.getToUserId());
        assertNull(vo.getToName());
        assertNull(vo.getToEmail());
        assertNull(vo.getSubject());
        assertNull(vo.getContent());
    }
}
