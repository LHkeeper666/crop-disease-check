package com.agriculture.controller;

import com.agriculture.modules.agriBrain.controller.AiConversationController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 农业大脑对话表 控制器测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AiConversationController 农业大脑对话表控制器测试")
class AiConversationControllerTest {

    @InjectMocks
    private AiConversationController aiConversationController;

    @Test
    @DisplayName("控制器实例化成功")
    void controller_canBeInstantiated() {
        assertNotNull(aiConversationController);
    }
}
