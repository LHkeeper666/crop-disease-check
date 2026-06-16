package com.agriculture.controller;

import com.agriculture.modules.user.controller.SysUserController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 用户表 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SysUserController 用户表控制器测试")
class SysUserControllerTest {

    @InjectMocks
    private SysUserController sysUserController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(sysUserController);
    }
}
