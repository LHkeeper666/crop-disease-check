package com.agriculture.controller;

import com.agriculture.modules.pestDiseaseInfo.controller.PreventionPlanController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 防治方案表 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PreventionPlanController 防治方案表控制器测试")
class PreventionPlanControllerTest {

    @InjectMocks
    private PreventionPlanController preventionPlanController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(preventionPlanController);
    }
}
