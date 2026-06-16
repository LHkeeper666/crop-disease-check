package com.agriculture.controller;

import com.agriculture.modules.pestDiseaseInfo.controller.PestInfoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 虫害信息表 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PestInfoController 虫害信息表控制器测试")
class PestInfoControllerTest {

    @InjectMocks
    private PestInfoController pestInfoController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(pestInfoController);
    }
}
