package com.agriculture.controller;

import com.agriculture.modules.agriBrain.controller.AiMessageController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 农业大脑消息表 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiMessageController 农业大脑消息表控制器测试")
class AiMessageControllerTest {

    @InjectMocks
    private AiMessageController aiMessageController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(aiMessageController);
    }
}
