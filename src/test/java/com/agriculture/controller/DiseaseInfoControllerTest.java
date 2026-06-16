package com.agriculture.controller;

import com.agriculture.modules.pestDiseaseInfo.controller.DiseaseInfoController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 病害信息表 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DiseaseInfoController 病害信息表控制器测试")
class DiseaseInfoControllerTest {

    @InjectMocks
    private DiseaseInfoController diseaseInfoController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(diseaseInfoController);
    }
}
