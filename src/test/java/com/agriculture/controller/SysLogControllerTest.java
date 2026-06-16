package com.agriculture.controller;

import com.agriculture.modules.system.controller.SysLogController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 操作日志表 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SysLogController 操作日志表控制器测试")
class SysLogControllerTest {

    @InjectMocks
    private SysLogController sysLogController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(sysLogController);
    }
}
