package com.agriculture.websocket;

import com.agriculture.common.websocket.JwtUtils;
import com.agriculture.common.websocket.WebSocketAuthInterceptor;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketAuthInterceptor 单元测试")
class WebSocketAuthInterceptorTest {

    @Mock
    private JwtUtils jwtUtils;

    @InjectMocks
    private WebSocketAuthInterceptor interceptor;

    @Mock
    private MessageChannel channel;

    private Message<?> createStompMessage(StompCommand command, String authHeader) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(command);
        if (authHeader != null) {
            accessor.addNativeHeader("Authorization", authHeader);
        }
        accessor.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
    }

    @Test
    @DisplayName("有效 Token - 认证成功")
    void preSend_validToken_authenticates() {
        when(jwtUtils.validateToken("valid-token")).thenReturn(true);
        when(jwtUtils.getUserId("valid-token")).thenReturn("user-001");
        when(jwtUtils.getUsername("valid-token")).thenReturn("testuser");

        Message<?> message = createStompMessage(StompCommand.CONNECT, "Bearer valid-token");

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
    }

    @Test
    @DisplayName("无效 Token - 抛出异常")
    void preSend_invalidToken_throwsException() {
        when(jwtUtils.validateToken("invalid-token")).thenReturn(false);

        Message<?> message = createStompMessage(StompCommand.CONNECT, "Bearer invalid-token");

        assertThrows(IllegalArgumentException.class, () -> {
            interceptor.preSend(message, channel);
        });
    }

    @Test
    @DisplayName("缺少 Authorization header - 抛出异常")
    void preSend_missingAuthHeader_throwsException() {
        Message<?> message = createStompMessage(StompCommand.CONNECT, null);

        assertThrows(IllegalArgumentException.class, () -> {
            interceptor.preSend(message, channel);
        });
    }

    @Test
    @DisplayName("非 CONNECT 帧 - 直接返回")
    void preSend_nonConnectFrame_returnsDirectly() {
        Message<?> message = createStompMessage(StompCommand.SUBSCRIBE, "Bearer valid-token");

        Message<?> result = interceptor.preSend(message, channel);

        assertNotNull(result);
        assertEquals(message, result);
    }

    @Test
    @DisplayName("CONNECT 帧但 Authorization 格式错误 - 抛出异常")
    void preSend_malformedAuthHeader_throwsException() {
        Message<?> message = createStompMessage(StompCommand.CONNECT, "Basic dXNlcjpwYXNz");

        assertThrows(IllegalArgumentException.class, () -> {
            interceptor.preSend(message, channel);
        });
    }

    @Test
    @DisplayName("Token 过期 - 抛出异常")
    void preSend_expiredToken_throwsException() {
        when(jwtUtils.validateToken("expired-token")).thenReturn(false);

        Message<?> message = createStompMessage(StompCommand.CONNECT, "Bearer expired-token");

        assertThrows(IllegalArgumentException.class, () -> {
            interceptor.preSend(message, channel);
        });
    }
}
