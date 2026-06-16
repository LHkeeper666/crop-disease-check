package com.agriculture.controller;

import com.agriculture.modules.pestDiseaseInfo.controller.PreventionPlanVersionController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 防治方案版本历史 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PreventionPlanVersionController 防治方案版本历史控制器测试")
class PreventionPlanVersionControllerTest {

    @InjectMocks
    private PreventionPlanVersionController preventionPlanVersionController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(preventionPlanVersionController);
    }
}
