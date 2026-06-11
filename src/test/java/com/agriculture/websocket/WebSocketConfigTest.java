package com.agriculture.websocket;

import com.agriculture.common.websocket.WebSocketAuthInterceptor;

import com.agriculture.common.config.WebSocketConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;

import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebSocketConfig 单元测试")
class WebSocketConfigTest {

    @Mock
    private WebSocketAuthInterceptor webSocketAuthInterceptor;

    @InjectMocks
    private WebSocketConfig webSocketConfig;

    @Test
    @DisplayName("configureMessageBroker - 配置消息代理")
    void configureMessageBroker_configuresBroker() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        webSocketConfig.configureMessageBroker(registry);

        verify(registry).enableSimpleBroker("/topic", "/queue");
        verify(registry).setApplicationDestinationPrefixes("/app");
        verify(registry).setUserDestinationPrefix("/user");
    }

    @Test
    @DisplayName("registerStompEndpoints - 注册 STOMP 端点")
    void registerStompEndpoints_registersEndpoint() {
        // 由于 StompEndpointRegistry 的 addEndpoint 返回链式调用对象，需要使用 doNothing
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class, RETURNS_DEEP_STUBS);

        webSocketConfig.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/ws");
    }

    @Test
    @DisplayName("configureClientInboundChannel - 注册认证拦截器")
    void configureClientInboundChannel_registersInterceptor() {
        ChannelRegistration registration = mock(ChannelRegistration.class);

        webSocketConfig.configureClientInboundChannel(registration);

        verify(registration).interceptors(webSocketAuthInterceptor);
    }
}
