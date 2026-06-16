package com.agriculture.controller;

import com.agriculture.modules.camera.controller.CameraGridController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 摄像头覆盖网格关联 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("CameraGridController 摄像头覆盖网格关联控制器测试")
class CameraGridControllerTest {

    @InjectMocks
    private CameraGridController cameraGridController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(cameraGridController);
    }
}
