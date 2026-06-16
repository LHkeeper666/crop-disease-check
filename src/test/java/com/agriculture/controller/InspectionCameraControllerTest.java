package com.agriculture.controller;

import com.agriculture.modules.inspection.controller.InspectionCameraController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 巡检计划摄像头关联 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InspectionCameraController 巡检计划摄像头关联控制器测试")
class InspectionCameraControllerTest {

    @InjectMocks
    private InspectionCameraController inspectionCameraController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(inspectionCameraController);
    }
}
