package com.agriculture.controller;

import com.agriculture.modules.pestDiseaseInfo.controller.AuditRecordController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 审核记录表 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuditRecordController 审核记录表控制器测试")
class AuditRecordControllerTest {

    @InjectMocks
    private AuditRecordController auditRecordController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(auditRecordController);
    }
}
