package com.agriculture.websocket;

import com.agriculture.common.websocket.WebSocketAuthInterceptor;
import com.agriculture.common.websocket.WebSocketMessage;
import com.agriculture.common.websocket.WebSocketMessageType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("WebSocket 消息类型常量测试")
@ExtendWith(MockitoExtension.class)
class WebSocketIntegrationTest {

    @Test
    @DisplayName("消息类型常量值正确")
    void messageTypeConstants_haveCorrectValues() {
        assertEquals("INFERENCE_RESULT", WebSocketMessageType.INFERENCE_RESULT);
        assertEquals("WORKORDER_STATUS_CHANGE", WebSocketMessageType.WORKORDER_STATUS_CHANGE);
        assertEquals("HEATMAP_UPDATE", WebSocketMessageType.HEATMAP_UPDATE);
        assertEquals("INSPECTION_STATUS", WebSocketMessageType.INSPECTION_STATUS);
    }

    @Test
    @DisplayName("WebSocketUserPrincipal - getUserId 返回用户 ID")
    void webSocketUserPrincipal_getUserId_returnsUserId() {
        WebSocketAuthInterceptor.WebSocketUserPrincipal principal =
                new WebSocketAuthInterceptor.WebSocketUserPrincipal("user-001", "testuser");

        assertEquals("user-001", principal.getUserId());
    }

    @Test
    @DisplayName("WebSocketUserPrincipal - getName 返回用户名")
    void webSocketUserPrincipal_getName_returnsUsername() {
        WebSocketAuthInterceptor.WebSocketUserPrincipal principal =
                new WebSocketAuthInterceptor.WebSocketUserPrincipal("user-001", "testuser");

        assertEquals("testuser", principal.getName());
    }

    @Test
    @DisplayName("WebSocketMessage - 泛型类型支持")
    void webSocketMessage_supportsGenericTypes() {
        // String 类型
        WebSocketMessage<String> stringMsg = WebSocketMessage.of("TYPE", "data");
        assertEquals("data", stringMsg.getData());

        // Integer 类型
        WebSocketMessage<Integer> intMsg = WebSocketMessage.of("TYPE", 123);
        assertEquals(123, intMsg.getData());

        // Map 类型
        java.util.Map<String, Object> mapData = new java.util.HashMap<>();
        mapData.put("key", "value");
        WebSocketMessage<java.util.Map<String, Object>> mapMsg = WebSocketMessage.of("TYPE", mapData);
        assertEquals("value", mapMsg.getData().get("key"));
    }
}
