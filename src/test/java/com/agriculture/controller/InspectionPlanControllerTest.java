package com.agriculture.controller;

import com.agriculture.modules.inspection.controller.InspectionPlanController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 巡检计划表 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InspectionPlanController 巡检计划表控制器测试")
class InspectionPlanControllerTest {

    @InjectMocks
    private InspectionPlanController inspectionPlanController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(inspectionPlanController);
    }
}
