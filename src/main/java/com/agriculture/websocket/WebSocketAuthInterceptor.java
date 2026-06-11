package com.agriculture.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.security.Principal;
import java.util.Map;

/**
 * WebSocket 认证拦截器
 * 从 STOMP CONNECT 帧的 Authorization header 中提取 JWT Token 进行认证
 */
@Slf4j
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Resource
    private JwtUtils jwtUtils;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            String authHeader = accessor.getFirstNativeHeader("Authorization");

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);

                if (jwtUtils.validateToken(token)) {
                    String userId = jwtUtils.getUserId(token);
                    String username = jwtUtils.getUsername(token);

                    // 设置用户身份信息
                    WebSocketUserPrincipal principal = new WebSocketUserPrincipal(userId, username);
                    accessor.setUser(principal);

                    log.info("WebSocket 认证成功: userId={}, username={}", userId, username);
                } else {
                    log.warn("WebSocket 认证失败: Token 无效或已过期");
                    throw new IllegalArgumentException("Token 无效或已过期");
                }
            } else {
                log.warn("WebSocket 认证失败: 缺少 Authorization header");
                throw new IllegalArgumentException("缺少认证信息");
            }
        }

        return message;
    }

    /**
     * WebSocket 用户身份信息
     */
    public static class WebSocketUserPrincipal implements Principal {
        private final String userId;
        private final String username;

        public WebSocketUserPrincipal(String userId, String username) {
            this.userId = userId;
            this.username = username;
        }

        public String getUserId() {
            return userId;
        }

        @Override
        public String getName() {
            return username;
        }
    }
}
